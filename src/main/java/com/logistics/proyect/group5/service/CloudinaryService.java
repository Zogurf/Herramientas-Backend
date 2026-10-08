package com.logistics.proyect.group5.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadImage(MultipartFile file) {
        validateImage(file);

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", "image",
                            "folder", "nexora/products"
                    )
            );

            String secureUrl = (String) uploadResult.get("secure_url");
            if (secureUrl == null || secureUrl.isBlank()) {
                throw new IllegalStateException("Cloudinary no devolvió la URL de la imagen.");
            }
            return secureUrl;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir la imagen a Cloudinary.", e);
        }
    }

    public void deleteImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(
                    extractPublicId(imageUrl),
                    ObjectUtils.asMap("resource_type", "image")
            );
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo eliminar la imagen de Cloudinary.", e);
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("La imagen no puede estar vacía.");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("La imagen no puede superar los 10 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("Solo se permiten archivos de imagen.");
        }
    }

    private String extractPublicId(String imageUrl) {
        int uploadIndex = imageUrl.indexOf("/upload/");
        if (uploadIndex < 0) {
            throw new IllegalArgumentException("La URL de Cloudinary no es válida.");
        }

        String publicId = imageUrl.substring(uploadIndex + "/upload/".length());
        if (publicId.startsWith("v")) {
            int versionSeparator = publicId.indexOf('/');
            if (versionSeparator > 0) {
                publicId = publicId.substring(versionSeparator + 1);
            }
        }

        int extensionIndex = publicId.lastIndexOf('.');
        return extensionIndex > 0 ? publicId.substring(0, extensionIndex) : publicId;
    }
}
