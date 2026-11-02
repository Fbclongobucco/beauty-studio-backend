package com.longobuccodev.beautystudio.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * @param corsAllowedOrigins origens do front com credenciais (cookie de sessao)
 * @param mediaUri           endereco interno do armazenamento de fotos (MinIO)
 * @param timeout            tempo maximo de resposta de um servico antes do fallback 503
 * @param openStateWait      tempo com o circuito aberto antes de testar o servico de novo
 */
@ConfigurationProperties("beauty.gateway")
public record GatewayProperties(List<String> corsAllowedOrigins, URI mediaUri, Duration timeout,
                                Duration openStateWait) {
}
