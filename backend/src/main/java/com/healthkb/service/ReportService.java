package com.healthkb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.healthkb.common.AppException;
import com.healthkb.common.MedicalConstants;
import com.healthkb.entity.ExamReport;
import com.healthkb.entity.ExamReportItem;
import com.healthkb.mapper.ExamReportItemMapper;
import com.healthkb.mapper.ExamReportMapper;
import com.healthkb.rag.DocumentParser;
import com.healthkb.rag.LlmClient;
import com.healthkb.rag.RagService;
import com.healthkb.rag.ScoredChunk;
import com.healthkb.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ExamReportMapper reportMapper;
    private final ExamReportItemMapper itemMapper;
    private final DocumentParser documentParser;
    private final ReportParser reportParser;
    private final RagService ragService;
    private final LlmClient llmClient;
    private final HealthService healthService;

    @Transactional
    public Map<String, Object> upload(MultipartFile file, String extractedText, Long profileId) {
        String text = documentParser.extractText(file, extractedText);
        if (text.isBlank()) {
            throw AppException.badRequest("未能识别报告文字。图片请粘贴提取文本，或上传文件名含 demo 的示例图 / PDF / Word / txt");
        }
        Long userId = SecurityUtils.currentUserId();
        ExamReport report = new ExamReport();
        report.setUserId(userId);
        report.setProfileId(profileId);
        report.setFilename(file.getOriginalFilename());
        report.setRawText(text);
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);

        List<ExamReportItem> items = reportParser.parse(text);
        String query = items.stream().map(ExamReportItem::getName).collect(Collectors.joining(" "));
        List<ScoredChunk> chunks = ragService.retrieve((query.isBlank() ? text : query) + " 体检指标", 5);

        StringBuilder itemDigest = new StringBuilder();
        for (ExamReportItem item : items) {
            item.setReportId(report.getId());
            item.setInterpretation(reportParser.interpretItem(item));
            itemMapper.insert(item);
            itemDigest.append(item.getName()).append(" ").append(item.getItemValue())
                    .append(item.getUnit() == null ? "" : item.getUnit())
                    .append(" ").append(item.getFlag()).append("\n");
        }

        String extra = "已解析指标：\n" + (itemDigest.isEmpty() ? "未能自动拆分指标，请参考原文。\n" : itemDigest)
                + "\n原文摘录：\n" + brief(text, 600);
        String summary = llmClient.generateSync("请逐项解读这份体检/检查报告中的异常指标，并给出总体建议", chunks, List.of(), extra);
        if (!summary.contains("仅供健康科普参考")) {
            summary = summary + "\n\n" + MedicalConstants.DISCLAIMER;
        }
        report.setSummary(summary);
        reportMapper.updateById(report);
        if (profileId != null) {
            healthService.importReportItems(profileId, items, report.getFilename(), report.getCreatedAt());
        }
        return detail(report.getId());
    }

    public Map<String, Object> importToProfile(Long reportId, Long profileId) {
        Map<String, Object> d = detail(reportId);
        @SuppressWarnings("unchecked")
        List<ExamReportItem> items = (List<ExamReportItem>) d.get("items");
        ExamReport report = (ExamReport) d.get("report");
        int n = healthService.importReportItems(profileId, items, report.getFilename(), report.getCreatedAt());
        report.setProfileId(profileId);
        reportMapper.updateById(report);
        Map<String, Object> out = new HashMap<>(d);
        out.put("imported", n);
        return out;
    }

    public List<ExamReport> listMine() {
        return reportMapper.selectList(new LambdaQueryWrapper<ExamReport>()
                .eq(ExamReport::getUserId, SecurityUtils.currentUserId())
                .orderByDesc(ExamReport::getCreatedAt));
    }

    public Map<String, Object> detail(Long id) {
        ExamReport report = reportMapper.selectById(id);
        if (report == null || !SecurityUtils.currentUserId().equals(report.getUserId())) {
            throw AppException.notFound("报告不存在");
        }
        List<ExamReportItem> items = itemMapper.selectList(new LambdaQueryWrapper<ExamReportItem>()
                .eq(ExamReportItem::getReportId, id)
                .orderByAsc(ExamReportItem::getId));
        Map<String, Object> map = new HashMap<>();
        map.put("report", report);
        map.put("items", items);
        map.put("disclaimer", MedicalConstants.DISCLAIMER);
        return map;
    }

    private static String brief(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
