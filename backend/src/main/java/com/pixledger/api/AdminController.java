package com.pixledger.api;

import com.pixledger.config.AppProperties;
import com.pixledger.mapper.LedgerMapper;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final LedgerMapper mapper;
    private final PasswordEncoder passwords;
    private final AppProperties props;

    public AdminController(LedgerMapper mapper, PasswordEncoder passwords, AppProperties props) {
        this.mapper = mapper;
        this.passwords = passwords;
        this.props = props;
    }

    record CreateUser(String phone, String password) {
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> create(@RequestHeader("X-Admin-Key") String key, @RequestBody CreateUser input) {
        if (props.adminBootstrapKey().isBlank() || !props.adminBootstrapKey().equals(key))
            throw new SecurityException("无权限");
        if (input.phone() == null || !input.phone().matches("^1\\d{10}$"))
            throw new IllegalArgumentException("请输入中国大陆手机号");
        if (input.password() == null || input.password().length() < 10)
            throw new IllegalArgumentException("密码至少需要 10 位");
        if (mapper.userByPhone(input.phone()) != null) throw new IllegalArgumentException("该手机号已存在");
        var user = new java.util.HashMap<String, Object>();
        user.put("phone", input.phone());
        user.put("hash", passwords.encode(input.password()));
        mapper.createUser(user);
        return Map.of("id", user.get("id"), "phone", input.phone());
    }
}
