package com.qingluo.link.service.impl.document.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qingluo.link.core.exception.BusinessException;
import com.qingluo.link.service.config.DocumentFileProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownAssetContentValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsImageContentAndProducesStableHash() throws Exception {
        Path image = tempDir.resolve("diagram.png");
        Files.write(image, new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3
        });
        MarkdownAssetContentValidator validator = validator(1024);

        MarkdownAssetContentValidator.ValidatedAsset validated = validator.validate(
            new MarkdownAssetFile("images/diagram.png", "diagram.png", image, "image/png", Files.size(image)));

        assertThat(validated.canonicalExtension()).isEqualTo("png");
        assertThat(validated.mimeType()).isEqualTo("image/png");
        assertThat(validated.sha256()).hasSize(64);
    }

    @Test
    void acceptsEverySupportedExtensionWithMatchingMimeAndMagic() throws Exception {
        List<ImageCase> cases = List.of(
            new ImageCase("photo.jpg", "image/jpeg", "jpg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}),
            new ImageCase("photo.jpeg", "image/jpeg", "jpg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}),
            new ImageCase("diagram.png", "image/png", "png",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a}),
            new ImageCase("animation.gif", "image/gif", "gif", "GIF89a".getBytes()),
            new ImageCase("photo.webp", "image/webp", "webp",
                new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}),
            new ImageCase("bitmap.bmp", "image/bmp", "bmp", new byte[] {'B', 'M', 1, 2}),
            new ImageCase("scan.tif", "image/tiff", "tiff", new byte[] {'I', 'I', 42, 0}),
            new ImageCase("scan.tiff", "image/tiff", "tiff", new byte[] {'M', 'M', 0, 42})
        );

        for (ImageCase imageCase : cases) {
            Path image = tempDir.resolve(imageCase.filename());
            Files.write(image, imageCase.magic());
            MarkdownAssetContentValidator.ValidatedAsset validated = validator(1024).validate(
                new MarkdownAssetFile(
                    imageCase.filename(), imageCase.filename(), image, imageCase.mime(), Files.size(image)));

            assertThat(validated.canonicalExtension())
                .as(imageCase.filename())
                .isEqualTo(imageCase.canonicalExtension());
            assertThat(validated.mimeType()).as(imageCase.filename()).isEqualTo(imageCase.mime());
        }
    }

    @Test
    void rejectsExtensionMimeAndMagicNumberMismatch() throws Exception {
        Path image = tempDir.resolve("fake.png");
        Files.writeString(image, "not an image");

        assertThatThrownBy(() -> validator(1024).validate(
            new MarkdownAssetFile("fake.png", "fake.png", image, "image/png", Files.size(image))))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(30014);
    }

    @Test
    void enforcesPerAssetLimitBeforeReadingContent() throws Exception {
        Path image = tempDir.resolve("large.png");
        Files.write(image, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});

        assertThatThrownBy(() -> validator(4).validate(
            new MarkdownAssetFile("large.png", "large.png", image, "image/png", Files.size(image))))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo(30016);
    }

    private MarkdownAssetContentValidator validator(long maxBytes) {
        DocumentFileProperties properties = new DocumentFileProperties();
        properties.setMarkdownAssetMaxBytes(maxBytes);
        return new MarkdownAssetContentValidator(properties);
    }

    private record ImageCase(
        String filename,
        String mime,
        String canonicalExtension,
        byte[] magic
    ) {
    }
}
