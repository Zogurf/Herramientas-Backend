package com.logistics.proyect.group5;

import com.logistics.proyect.group5.dto.CategoryRequest;
import com.logistics.proyect.group5.dto.ProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ApplicationTests {

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void omittingCategoryIdDoesNotMarkItProvided() {
		ProductRequest request = jsonMapper.readValue(
				"{\"name\":\"Teclado\",\"price\":10}", ProductRequest.class);

		assertEquals(new BigDecimal("10"), request.getPrice());
		assertNull(request.getCategoryId());
		assertFalse(request.isCategoryIdProvided());
	}

	@Test
	void explicitNullCategoryIdMarksItProvided() {
		ProductRequest request = jsonMapper.readValue(
				"{\"categoryId\":null}", ProductRequest.class);

		assertNull(request.getCategoryId());
		assertTrue(request.isCategoryIdProvided());
	}

	@Test
	void explicitCategoryIdMarksItProvided() {
		ProductRequest request = jsonMapper.readValue(
				"{\"categoryId\":7}", ProductRequest.class);

		assertEquals(7, request.getCategoryId());
		assertTrue(request.isCategoryIdProvided());
	}

	@Test
	void omittingParentIdDoesNotMarkItProvided() {
		CategoryRequest request = jsonMapper.readValue(
				"{\"name\":\"Categoria\"}", CategoryRequest.class);

		assertNull(request.getParentId());
		assertFalse(request.isParentIdProvided());
	}

	@Test
	void explicitNullParentIdMarksItProvided() {
		CategoryRequest request = jsonMapper.readValue(
				"{\"parentId\":null}", CategoryRequest.class);

		assertNull(request.getParentId());
		assertTrue(request.isParentIdProvided());
	}
}
