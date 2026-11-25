package com.campusmarket.item;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.campusmarket.security.MarketPrincipal;

@RestController
public class ItemImageController {
    private final Path directory;
    private final MarketItemService market;
    private final MarketItemMapper items;

    public ItemImageController(MarketItemService market, MarketItemMapper items,
                               @Value("${app.images.directory}") String directory) {
        this.market = market;
        this.items = items;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @PostMapping("/api/seller/items/{id}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional(rollbackFor = IOException.class)
    public void upload(@AuthenticationPrincipal MarketPrincipal seller, @PathVariable long id,
                       @RequestParam("file") MultipartFile file) throws IOException {
        var item = market.lockOwned(seller.id(), id);
        MarketItemService.requireState("DRAFT".equals(item.status()), "请在草稿状态上传图片。");
        String extension = extension(file);
        Files.createDirectories(directory);
        String key = UUID.randomUUID() + extension;
        Path target = directory.resolve(key);
        String previous = items.imageKey(id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    if (status == STATUS_COMMITTED && safeKey(previous)) {
                        Files.deleteIfExists(directory.resolve(previous));
                    } else if (status == STATUS_ROLLED_BACK) {
                        Files.deleteIfExists(target);
                    }
                } catch (IOException ignored) {
                    // The database remains the source for the active image.
                }
            }
        });
        file.transferTo(target);
        items.setImage(id, key);
    }

    @GetMapping("/api/items/{id}/image")
    public ResponseEntity<FileSystemResource> image(@PathVariable long id) {
        return resource(items.publicImageKey(id));
    }

    @GetMapping("/api/admin/reviews/{id}/image")
    public ResponseEntity<FileSystemResource> reviewImage(@PathVariable long id) {
        return resource(items.pendingImageKey(id));
    }

    private ResponseEntity<FileSystemResource> resource(String key) {
        if (!safeKey(key) || !Files.isRegularFile(directory.resolve(key))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品图片不存在。");
        }
        return ResponseEntity.ok().contentType(key.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .body(new FileSystemResource(directory.resolve(key)));
    }

    private String extension(MultipartFile file) throws IOException {
        try (var stream = file.getInputStream(); var image = ImageIO.createImageInputStream(stream)) {
            if (image != null) {
                var readers = ImageIO.getImageReaders(image);
                if (readers.hasNext()) {
                    var reader = readers.next();
                    try {
                        reader.setInput(image, true, true);
                        if (reader.getWidth(0) <= 4000 && reader.getHeight(0) <= 4000) {
                            String format = reader.getFormatName();
                            if ("PNG".equalsIgnoreCase(format)) {
                                return ".png";
                            }
                            if ("JPEG".equalsIgnoreCase(format)) {
                                return ".jpg";
                            }
                        }
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (javax.imageio.IIOException invalid) {
            throw unsupportedImage();
        }
        throw unsupportedImage();
    }

    private ResponseStatusException unsupportedImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "请上传尺寸不超过 4000 像素的 JPG 或 PNG 图片。");
    }

    private boolean safeKey(String key) {
        return key != null && key.matches("[0-9a-f-]{36}\\.(png|jpg)");
    }
}
