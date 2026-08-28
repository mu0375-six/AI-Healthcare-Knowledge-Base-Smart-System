package com.healthkb.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 高德地图 Web 服务封装：地理编码（地址 → 坐标）+ 周边检索（坐标 → 真实 POI）。
 *
 * 数据边界约定：医院/药店的名称、地址、电话、距离一律来自高德返回的真实数据，
 * 本类绝不构造机构信息 —— 大模型只对这里查到的列表做解释，避免编造不存在的医院。
 * 未配置 AMAP_KEY 或请求失败时返回空列表，由调用方走「科室就医建议」兜底文案。
 */
@Slf4j
@Service
public class AmapService {

    /** 高德个人开发者的免费 key，从 AMAP_KEY 环境变量或根目录 .env 读取。 */
    @Value("${AMAP_KEY:}")
    private String apiKey;

    private final ObjectMapper objectMapper;

    public AmapService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Data
    public static class Poi {
        private String name;
        private String address;
        private String tel;
        /** 周边检索时返回，单位米。 */
        private String distance;
        /** 形如「医疗保健服务;医院;综合医院」，取末段做展示标签。 */
        private String type;
    }

    public record Geocode(double lng, double lat, String formatted) {
    }

    /** 地址文本 → 坐标。解析失败（网络/无结果/key 无效）返回 empty。 */
    public Optional<Geocode> geocode(String address) {
        if (!isConfigured() || address == null || address.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = "https://restapi.amap.com/v3/geocode/geo?key=" + apiKey
                    + "&address=" + URLEncoder.encode(address, StandardCharsets.UTF_8);
            JsonNode root = getJson(url);
            if (root == null || !"1".equals(root.path("status").asText())) {
                return Optional.empty();
            }
            JsonNode first = root.path("geocodes").path(0);
            String location = first.path("location").asText("");
            if (location.isBlank() || !location.contains(",")) {
                return Optional.empty();
            }
            String[] parts = location.split(",");
            return Optional.of(new Geocode(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    first.path("formatted").asText(address)));
        } catch (Exception e) {
            log.warn("高德地理编码失败 {}: {}", address, e.getMessage());
            return Optional.empty();
        }
    }

    /** 坐标周边检索。types 传高德 POI 大类（090100=医院，090600=医药保健销售店）。 */
    public List<Poi> around(double lng, double lat, String types, int radiusMeters, int limit) {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            String url = "https://restapi.amap.com/v3/place/around?key=" + apiKey
                    + "&location=" + lng + "," + lat
                    + "&types=" + URLEncoder.encode(types, StandardCharsets.UTF_8)
                    + "&radius=" + radiusMeters
                    + "&offset=" + limit + "&page=1&sortrule=distance";
            JsonNode root = getJson(url);
            List<Poi> out = new ArrayList<>();
            if (root == null || !"1".equals(root.path("status").asText())) {
                return out;
            }
            for (JsonNode n : root.path("pois")) {
                Poi p = new Poi();
                p.setName(n.path("name").asText(""));
                String addr = n.path("address").asText("");
                if (addr.startsWith("[") && addr.endsWith("]")) {
                    addr = addr.substring(1, addr.length() - 1).replace("\"", "").replace(",", "");
                }
                p.setAddress(addr);
                JsonNode tel = n.path("tel");
                p.setTel(tel.isArray() ? tel.path(0).asText("") : tel.asText(""));
                p.setDistance(n.path("distance").asText(""));
                String type = n.path("type").asText("");
                int i = type.lastIndexOf(';');
                p.setType(i >= 0 ? type.substring(i + 1) : type);
                if (!p.getName().isBlank()) {
                    out.add(p);
                }
                if (out.size() >= limit) {
                    break;
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("高德周边检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    private JsonNode getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "healthkb-nearby/1.0")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("高德接口 HTTP {}: {}", resp.statusCode(), url.replaceAll("key=[^&]+", "key=***"));
                return null;
            }
            return objectMapper.readTree(resp.body());
        } catch (Exception e) {
            log.warn("高德接口请求失败: {}", e.getMessage());
            return null;
        }
    }
}
