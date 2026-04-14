package com.algaworks.algashop.authorizationserver.infrastructure.persistence;

import com.algaworks.algashop.authorizationserver.infrastructure.security.query.OAuth2AuthorizationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JdbcOAuth2AuthorizationQueryService implements OAuth2AuthorizationQueryService {

	private final JdbcOperations jdbcOperations;
	private final String sql = "SELECT id FROM oauth2_authorization WHERE principal_name = ?";

	@Override
	public List<String> findAuthorizationIds(String principalName) {
		return jdbcOperations.queryForList(sql, String.class, principalName);
	}
}
