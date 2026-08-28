package com.healthkb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.healthkb.common.AppException;
import com.healthkb.dto.TriageDtos;
import com.healthkb.entity.UserLocation;
import com.healthkb.mapper.UserLocationMapper;
import com.healthkb.rag.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 导诊「附近医疗资源」：症状科室关键词 + 用户坐标 → 高德周边检索拿真实 POI
 * → LLM 只对真实列表做解释排序，绝不编造机构。
 *
 * 数据边界：医院/药店的名称、地址、电话、距离全部来自高德；LLM 输出的建议
 * 若引用了列表之外的机构，前端展示的是列表卡片 + 建议文字，卡片本身不会说谎。
 * 未配置 AMAP_KEY 或网络不可用时：机构列表为空，返回科室就医建议兜底文案。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NearbyService {

    /** 优先综合医院+专科医院；偏远处不足时退回全部医院大类（含社区卫生服务中心）。 */
    private static final String HOSPITAL_TYPES_PRIMARY = "090101|090102";
    private static final String HOSPITAL_TYPES_ANY = "090100";
    /** 090601=药房（真药店）；090600 大类会混进养生会所、保健用品店。 */
    private static final String PHARMACY_TYPES = "090601";
    private static final int RADIUS_METERS = 3000;

    private final AmapService amap;
    private final LlmClient llm;
    private final UserLocationMapper locationMapper;

    public TriageDtos.NearbyResponse nearby(TriageDtos.NearbyRequest req, Long userId) {
        double lng;
        double lat;
        String label;
        if (req.getLng() != null && req.getLat() != null && req.getLng() != 0 && req.getLat() != 0) {
            lng = req.getLng();
            lat = req.getLat();
            label = "当前定位";
        } else if (req.getAddress() != null && !req.getAddress().isBlank()) {
            Optional<AmapService.Geocode> gc = amap.geocode(req.getAddress().trim());
            if (gc.isEmpty()) {
                throw AppException.badRequest("地址没有解析出坐标，试试写到区一级，如：北京市海淀区");
            }
            lng = gc.get().lng();
            lat = gc.get().lat();
            label = gc.get().formatted();
        } else {
            throw AppException.badRequest("请先定位或填写地址");
        }

        if (Boolean.TRUE.equals(req.getSave()) && userId != null) {
            saveLocation(userId, label, lng, lat);
        }

        List<AmapService.Poi> hospitalPois = amap.around(lng, lat, HOSPITAL_TYPES_PRIMARY, RADIUS_METERS, 4);
        if (hospitalPois.size() < 2) {
            hospitalPois = amap.around(lng, lat, HOSPITAL_TYPES_ANY, RADIUS_METERS, 4);
        }
        List<AmapService.Poi> pharmacyPois = amap.around(lng, lat, PHARMACY_TYPES, RADIUS_METERS, 3);

        TriageDtos.NearbyResponse resp = new TriageDtos.NearbyResponse();
        resp.setLocationLabel(label);
        resp.setHospitals(toDto(hospitalPois));
        resp.setPharmacies(toDto(pharmacyPois));

        if (hospitalPois.isEmpty() && pharmacyPois.isEmpty()) {
            resp.setAdvice(fallbackAdvice(req.getDepartment(), req.getUrgency()));
            resp.setAdviceSource("template");
        } else {
            resp.setAdvice(llmAdvice(req, hospitalPois, pharmacyPois));
            resp.setAdviceSource("llm");
        }
        return resp;
    }

    /** 把真实 POI 交给 LLM 写建议；失败退回模板文案，前端永远有话说。 */
    private String llmAdvice(TriageDtos.NearbyRequest req,
                             List<AmapService.Poi> hospitals,
                             List<AmapService.Poi> pharmacies) {
        String fallback = fallbackAdvice(req.getDepartment(), req.getUrgency());
        if (!llm.isConfigured()) {
            return fallback;
        }
        StringBuilder list = new StringBuilder();
        for (AmapService.Poi p : hospitals) {
            list.append("{\"类别\":\"医院\",\"名称\":\"").append(esc(p.getName()))
                    .append("\",\"距离米\":").append(num(p.getDistance())).append("},");
        }
        for (AmapService.Poi p : pharmacies) {
            list.append("{\"类别\":\"药店\",\"名称\":\"").append(esc(p.getName()))
                    .append("\",\"距离米\":").append(num(p.getDistance())).append("},");
        }
        String context = "用户在科室导诊后希望知道去哪里就医。\n"
                + "- 用户症状：" + safe(req.getSymptoms()) + "\n"
                + "- 导诊建议科室：" + safe(req.getDepartment()) + "（紧急程度：" + safe(req.getUrgency()) + "）\n"
                + "- 以下是高德地图周边检索返回的真实机构（距离为直线距离，米）：[" + list + "]\n"
                + "请写一段 120 字以内的中文就医建议：结合症状与科室说明优先考虑哪一家、为什么"
                + "（匹配度与距离），如需买药可点出合适药店；到达前建议先电话确认。"
                + "紧急程度为 emergency 时提醒直奔最近医院急诊。"
                + "禁止编造列表之外的机构、地址或电话，不要给出诊断。";
        try {
            String out = llm.generateSync("请给出就医建议", List.of(), List.of(), context);
            // generateSync 成功时会带全站免责声明尾注；异常路径抛错，走下方兜底
            return out.strip();
        } catch (Exception e) {
            log.warn("附近机构 LLM 建议生成失败，回退模板: {}", e.getMessage());
            return fallback;
        }
    }

    private String fallbackAdvice(String department, String urgency) {
        String dept = department == null || department.isBlank() ? "相应科室" : department;
        if ("emergency".equals(urgency)) {
            return "症状评估为急诊级别：请直接前往最近的医院急诊，不要按科室挑选或等待。";
        }
        return "未获取到附近机构列表（未配置地图服务或网络不可用）。按导诊建议可挂「" + dept
                + "」；就诊前建议电话确认门诊安排，症状加重请及时前往医院。";
    }

    // ------------------------------------------------------------ saved location

    public TriageDtos.SavedLocation savedLocation(Long userId) {
        UserLocation row = locationMapper.selectOne(new LambdaQueryWrapper<UserLocation>()
                .eq(UserLocation::getUserId, userId).last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        TriageDtos.SavedLocation s = new TriageDtos.SavedLocation();
        s.setAddressText(row.getAddressText());
        s.setLng(row.getLongitude());
        s.setLat(row.getLatitude());
        s.setSavedAt(row.getSavedAt() == null ? null
                : row.getSavedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return s;
    }

    public void saveLocation(Long userId, String addressText, double lng, double lat) {
        UserLocation row = locationMapper.selectOne(new LambdaQueryWrapper<UserLocation>()
                .eq(UserLocation::getUserId, userId).last("LIMIT 1"));
        if (row == null) {
            row = new UserLocation();
            row.setUserId(userId);
        }
        row.setAddressText(clip(addressText, 200));
        row.setLongitude(lng);
        row.setLatitude(lat);
        row.setSavedAt(LocalDateTime.now());
        if (row.getId() == null) {
            locationMapper.insert(row);
        } else {
            locationMapper.updateById(row);
        }
    }

    public void clearLocation(Long userId) {
        locationMapper.delete(new LambdaQueryWrapper<UserLocation>()
                .eq(UserLocation::getUserId, userId));
    }

    // ------------------------------------------------------------ helpers

    private List<TriageDtos.NearbyPoi> toDto(List<AmapService.Poi> pois) {
        List<TriageDtos.NearbyPoi> out = new ArrayList<>();
        for (AmapService.Poi p : pois) {
            TriageDtos.NearbyPoi d = new TriageDtos.NearbyPoi();
            d.setName(p.getName());
            d.setAddress(p.getAddress());
            d.setTel(p.getTel());
            d.setTypeLabel(p.getType());
            d.setDistanceMeters(num(p.getDistance()));
            out.add(d);
        }
        return out;
    }

    private static Double num(String s) {
        try {
            return s == null || s.isBlank() ? null : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String safe(String s) {
        return s == null || s.isBlank() ? "未填写" : s;
    }

    private static String esc(String s) {
        return s.replace("\"", "'").replace("\n", " ");
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
