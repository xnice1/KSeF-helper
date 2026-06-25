package com.ksefhelper.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void preservesAValidRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "client-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (currentRequest, currentResponse) ->
                assertThat(RequestIdFilter.requestId((MockHttpServletRequest) currentRequest))
                        .isEqualTo("client-request-123");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-request-123");
    }

    @Test
    void replacesAnUnsafeRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "unsafe request id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (currentRequest, currentResponse) -> {
        });

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .isNotBlank()
                .doesNotContain(" ");
    }
}
