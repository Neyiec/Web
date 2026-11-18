package com.campusmarket.item;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {
    private final CatalogMapper catalogMapper;

    public CatalogController(CatalogMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    @GetMapping("/api/items")
    public List<CatalogItem> list() {
        return catalogMapper.findRecentPublicItems();
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail handleDatabaseError() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "暂时无法读取商品，请稍后重试。");
    }
}
