package com.healthkb.bootstrap;

import cn.dev33.satoken.secure.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.healthkb.common.MedicalConstants;
import com.healthkb.entity.SysUser;
import com.healthkb.mapper.KbDocumentMapper;
import com.healthkb.mapper.SysUserMapper;
import com.healthkb.rag.RagService;
import com.healthkb.service.KnowledgeService;
import com.healthkb.service.OfficialKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final KbDocumentMapper documentMapper;
    private final KnowledgeService knowledgeService;
    private final OfficialKnowledgeService officialKnowledgeService;
    private final RagService ragService;
    private final ObjectMapper objectMapper;

    /** 强制全量重建的逃生口：内容变了但条数没变时，数量比对发现不了，用它可以手动触发。 */
    @Value("${app.kb.force-reindex:false}")
    private boolean forceReindex;

    /** 演示账号开关。默认创建；对外部署或安全评审场景可设 APP_SEED_DEMO_ACCOUNTS=false 关闭。 */
    @Value("${app.seed-demo-accounts:true}")
    private boolean seedDemoAccounts;

    @Override
    public void run(ApplicationArguments args) {
        if (seedDemoAccounts) {
            ensureUser("admin", "Admin123!", "系统管理员", MedicalConstants.ROLE_ADMIN);
            ensureUser("user", "User123!", "演示用户", MedicalConstants.ROLE_USER);
        } else {
            log.info("已按配置跳过演示账号创建（app.seed-demo-accounts=false）");
        }
        seedOfficialKnowledge();
        boolean rebuilt = ragService.rebuildIfStale(forceReindex);
        if (!rebuilt) {
            log.info("启动完成（知识未变化，跳过重建），向量条数={}", ragService.vectorCount());
        } else {
            log.info("启动完成，向量条数={}", ragService.vectorCount());
        }
    }

    private void ensureUser(String username, String rawPassword, String nickname, String role) {
        Long n = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (n != null && n > 0) {
            return;
        }
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(BCrypt.hashpw(rawPassword, BCrypt.gensalt()));
        u.setNickname(nickname);
        u.setRole(role);
        u.setCreatedAt(LocalDateTime.now());
        userMapper.insert(u);
        log.info("已写入演示账号 {}", username);
    }

    private void seedOfficialKnowledge() {
        boolean replaceDemo = !officialKnowledgeService.hasOfficialDocuments();
        OfficialKnowledgeService.SyncResult result = officialKnowledgeService.sync(replaceDemo);
        log.info("权威知识同步：在线={} 快照={} 跳过={} 失败={} 清理演示={}",
                result.fetched, result.fromSnapshot, result.skipped, result.failed, result.removedDemo);
        Long n = documentMapper.selectCount(null);
        if (n != null && n > 0) {
            return;
        }
        try (InputStream in = new ClassPathResource("data/seed-knowledge.json").getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode arr = objectMapper.readTree(json);
            for (JsonNode node : arr) {
                knowledgeService.persistDocument(
                        node.path("title").asText("未命名"),
                        node.path("category").asText("疾病指南"),
                        node.path("source").asText("演示知识库"),
                        null,
                        node.path("content").asText(""));
            }
            log.warn("权威源不可用，已回退演示种子 {} 篇", arr.size());
        } catch (Exception e) {
            log.error("导入种子知识失败", e);
        }
    }
}
