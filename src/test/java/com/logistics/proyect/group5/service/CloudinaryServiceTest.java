package com.logistics.proyect.group5.service;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    @Mock
    private Cloudinary cloudinary;

    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        cloudinaryService = new CloudinaryService(cloudinary);
    }

    @Test
    void uploadRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "empty.png",
                "image/png",
                new byte[0]
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> cloudinaryService.uploadImage(file)
        );
    }

    @Test
    void uploadRejectsNonImageFile() {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "document.txt",
                "text/plain",
                "not an image".getBytes()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> cloudinaryService.uploadImage(file)
        );
    }
}
