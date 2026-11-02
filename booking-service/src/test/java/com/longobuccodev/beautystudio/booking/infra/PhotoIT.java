package com.longobuccodev.beautystudio.booking.infra;

import com.longobuccodev.beautystudio.booking.core.storage.PhotoStorage;
import com.longobuccodev.beautystudio.booking.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Seção 10: foto do serviço no MinIO, com o contrato da mudança add-service-photo do front. */
class PhotoIT extends IntegrationTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};
    static final byte[] PDF = "%PDF-1.7 documento".getBytes();

    @Autowired
    PhotoStorage storage;

    static MockMultipartHttpServletRequestBuilder put(String serviceId) {
        MockMultipartHttpServletRequestBuilder b = multipart("/api/admin/services/" + serviceId + "/photo");
        b.with(r -> {
            r.setMethod("PUT");
            return r;
        });
        return b;
    }

    /** URL pública → endereço do MinIO do teste (o gateway serve /media/** removendo o prefixo). */
    static HttpResponse<byte[]> fetch(String photoUrl) throws Exception {
        String path = photoUrl.replace("http://gateway.test/media", "");
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(MINIO.getS3URL() + path)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    String photoUrl(Cookie session, String serviceId, byte[] content) throws Exception {
        String res = mvc.perform(put(serviceId).file(new MockMultipartFile("photo", "foto.jpg", "image/jpeg", content))
                        .cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return res.replaceAll("(?s).*\"photoUrl\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void storageGravaLeERemoveObjetoPublico() throws Exception {
        storage.put("services/teste/a.jpg", JPEG, "image/jpeg");
        String url = storage.publicUrl("services/teste/a.jpg");
        assertThat(url).isEqualTo("http://gateway.test/media/service-photos/services/teste/a.jpg");
        HttpResponse<byte[]> res = fetch(url);
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).isEqualTo(JPEG);
        assertThat(res.headers().firstValue("Content-Type")).contains("image/jpeg");
        storage.delete("services/teste/a.jpg");
        assertThat(fetch(url).statusCode()).isEqualTo(404);
    }

    @Test
    void envioValidoAparecenoCatalogoPublico() throws Exception {
        String url = photoUrl(lorrayne(), "box-braids", JPEG);
        assertThat(url).startsWith("http://gateway.test/media/service-photos/services/box-braids/").endsWith(".jpg")
                .doesNotContain("foto.jpg");
        assertThat(mvc.perform(get("/api/services")).andReturn().getResponse().getContentAsString()).contains(url);
        assertThat(fetch(url).statusCode()).isEqualTo(200);
    }

    @Test
    void trocaMudaAUrlEAAnteriorDeixaDeServir() throws Exception {
        Cookie master = master();
        String first = photoUrl(master, "manicure", JPEG);
        String second = photoUrl(master, "manicure", JPEG);
        assertThat(second).isNotEqualTo(first);
        assertThat(fetch(first).statusCode()).isEqualTo(404);
        assertThat(fetch(second).statusCode()).isEqualTo(200);
    }

    @Test
    void pdfRenomeadoTamanhoECampoAusente() throws Exception {
        Cookie stefani = stefani();
        mvc.perform(put("manicure").file(new MockMultipartFile("photo", "foto.jpg", "image/jpeg", PDF)).cookie(stefani))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.photo").value("Envie uma foto em JPEG, PNG ou WebP."));
        byte[] big = Arrays.copyOf(JPEG, 6 * 1024 * 1024);
        mvc.perform(put("manicure").file(new MockMultipartFile("photo", "foto.jpg", "image/jpeg", big)).cookie(stefani))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.photo").value("A foto deve ter no máximo 5 MB."));
        mvc.perform(put("manicure").file(new MockMultipartFile("outro", "foto.jpg", "image/jpeg", JPEG)).cookie(stefani))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.photo").value("Escolha uma foto."));
        assertThat(jdbc.sql("select photo_key from service where id = 'manicure'").query(String.class).optional())
                .isEmpty();
    }

    @Test
    void servicoDeOutraProfissionalOuSemSessao() throws Exception {
        mvc.perform(put("manicure").file(new MockMultipartFile("photo", "foto.jpg", "image/jpeg", JPEG))
                        .cookie(michele()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(put("manicure").file(new MockMultipartFile("photo", "foto.jpg", "image/jpeg", JPEG)))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/services/manicure/photo")).andExpect(status().isUnauthorized());
    }

    @Test
    void remocaoEServicoSemFoto() throws Exception {
        Cookie master = master();
        String url = photoUrl(master, "manicure", JPEG);
        mvc.perform(delete("/api/admin/services/manicure/photo").cookie(master))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photoUrl").isEmpty());
        assertThat(fetch(url).statusCode()).isEqualTo(404);
        mvc.perform(delete("/api/admin/services/pedicure/photo").cookie(master))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photoUrl").isEmpty());
    }

    @Test
    void patchIgnoraPhotoUrl() throws Exception {
        Cookie master = master();
        String url = photoUrl(master, "manicure", JPEG);
        mvc.perform(json(patch("/api/admin/services/manicure").cookie(master), "{\"photoUrl\":null,\"name\":\"Manicure\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photoUrl").value(url));
    }

    @Test
    void minioForaDoArResponde503SemMudarAFotoECatalogoContinua() throws Exception {
        Cookie master = master();
        String url = photoUrl(master, "manicure", JPEG);
        MINIO.getDockerClient().pauseContainerCmd(MINIO.getContainerId()).exec();
        try {
            mvc.perform(put("manicure").file(new MockMultipartFile("photo", "nova.jpg", "image/jpeg", JPEG)).cookie(master))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
            mvc.perform(delete("/api/admin/services/manicure/photo").cookie(master))
                    .andExpect(status().isServiceUnavailable());
            mvc.perform(get("/api/services")).andExpect(status().isOk());
            assertThat(mvc.perform(get("/api/admin/services").cookie(master)).andReturn().getResponse()
                    .getContentAsString()).contains(url);
        } finally {
            MINIO.getDockerClient().unpauseContainerCmd(MINIO.getContainerId()).exec();
        }
    }
}
