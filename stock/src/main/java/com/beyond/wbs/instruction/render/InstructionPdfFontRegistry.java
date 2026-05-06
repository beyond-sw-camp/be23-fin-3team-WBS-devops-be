package com.beyond.wbs.instruction.render;

import com.lowagie.text.pdf.BaseFont;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.xhtmlrenderer.pdf.ITextFontResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 한글 폰트(NanumGothic)를 OS 임시 디렉터리에 한 번만 추출해두고,
 * 각 PDF 렌더 호출 때마다 ITextFontResolver에 같은 파일 경로로 등록한다.
 *
 * Flying Saucer는 ITextFontResolver.addFont(filePath, IDENTITY_H, EMBEDDED)로 폰트를 등록해야
 * CSS의 font-family 선언이 효력을 갖는다. 폰트 파일이 없으면 등록을 skip하고 경고 로그만 남긴다
 * — 한글이 깨질 뿐 PDF 생성 자체는 동작.
 */
@Component
@Slf4j
public class InstructionPdfFontRegistry {

    private static final String CLASSPATH_FONT = "fonts/NanumGothic-Regular.ttf";

    private Path extractedFontPath;
    private boolean available;

    @PostConstruct
    public void init() {
        ClassPathResource resource = new ClassPathResource(CLASSPATH_FONT);
        if (!resource.exists()) {
            log.warn("[InstructionPdf] 한글 폰트 파일이 없습니다: classpath:{} — PDF 한글이 깨질 수 있음. " +
                    "common/src/main/resources/fonts/README.md 참고.", CLASSPATH_FONT);
            this.available = false;
            return;
        }
        try {
            Path tempDir = Files.createTempDirectory("instruction-pdf-fonts-");
            tempDir.toFile().deleteOnExit();
            Path target = tempDir.resolve("NanumGothic-Regular.ttf");
            try (var in = resource.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            this.extractedFontPath = target;
            this.available = true;
            log.info("[InstructionPdf] 한글 폰트 추출 완료: {}", target);
        } catch (IOException e) {
            log.error("[InstructionPdf] 한글 폰트 추출 실패", e);
            this.available = false;
        }
    }

    public void registerInto(ITextFontResolver fontResolver) {
        if (!available || extractedFontPath == null) {
            return;
        }
        try {
            fontResolver.addFont(
                extractedFontPath.toAbsolutePath().toString(),
                BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED
            );
        } catch (IOException e) {
            log.error("[InstructionPdf] 한글 폰트 등록 실패: {}", extractedFontPath, e);
        }
    }
}
