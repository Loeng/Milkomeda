package com.github.yizzuide.milkomeda.demo.crust.controller;

import com.github.yizzuide.milkomeda.crust.CrustContext;
import com.github.yizzuide.milkomeda.crust.CrustUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * CaseController
 *
 * @author yizzuide
 * Create at 2019/11/12 00:09
 */
@Slf4j
@RestController
@RequestMapping("case")
public class CaseController {

    @GetMapping("info")
    @PreAuthorize("hasAuthority('case:find:one')")
    public Map<String, Object> info() {
        CrustUserInfo userInfo = CrustContext.get().getUserInfo();
        log.info("userInfo: {}", userInfo);
        Map<String, Object> data = new HashMap<>();
        data.put("id", "12345667009874");
        data.put("name", "case-01");
        return data;
    }
}