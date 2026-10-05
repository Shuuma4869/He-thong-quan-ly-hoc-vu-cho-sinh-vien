package vn.edu.phenikaa.ams.academic.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.phenikaa.ams.academic.application.CurriculumQueryService;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.config.SecurityConfiguration;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CurriculumController.class)
@Import(SecurityConfiguration.class)
class CurriculumControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CurriculumQueryService queries;
    @MockitoBean UserDetailsService users;
    private final AccountPrincipal principal = new AccountPrincipal(new AppUser("reader@example.test", "!synthetic-unusable", null));

    @Test void databaseFailureHasSafeCodeAndNeverLeaksSql() throws Exception {
        when(queries.catalog(any(), any())).thenThrow(new DataAccessResourceFailureException("select private_column from private_table; synthetic-private-id"));
        String body = mvc.perform(get("/api/me/academic/catalog/courses").with(user(principal)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("select", "private_column", "private_table", "synthetic-private-id", "stack");
    }

    @Test void invalidQueryNeverReachesRepository() throws Exception {
        mvc.perform(get("/api/me/academic/catalog/courses").param("limit", "101").with(user(principal)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));
        mvc.perform(get("/api/me/academic/curricula/not-a-uuid").with(user(principal)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));
        verifyNoInteractions(queries);
    }

    @Test void connectionFailureBeforeQueryUsesTheSameSafeUnavailableCode() throws Exception {
        when(queries.catalog(any(), any())).thenThrow(new org.springframework.transaction.CannotCreateTransactionException("synthetic-private-jdbc-url"));
        String body = mvc.perform(get("/api/me/academic/catalog/courses").with(user(principal)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("synthetic-private-jdbc-url");
    }
}
