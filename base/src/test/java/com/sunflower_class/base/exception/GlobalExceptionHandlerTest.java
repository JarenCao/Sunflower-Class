package com.sunflower_class.base.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 校验真实 MVC 异常响应，防止参数错误变成 500 或被当成视图解析。 */
class GlobalExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ValidationController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();

    @Test
    void invalidRequestReturnsChineseJsonWithBadRequest() throws Exception {
        mvc.perform(
            post("/validation").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")
        )
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errMessage").value("名称不能为空"));
    }

    @Test
    void explicitPermissionFailureKeepsForbiddenStatus() throws Exception {
        mvc.perform(get("/forbidden"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errMessage").value("当前账号没有此操作权限"));
    }

    @RestController
    static class ValidationController {

        @PostMapping("/validation")
        public void validate(@RequestBody @Valid ValidationForm form) {}

        @GetMapping("/forbidden")
        public void forbidden() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有此操作权限");
        }
    }

    static class ValidationForm {

        @NotBlank(message = "名称不能为空")
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
