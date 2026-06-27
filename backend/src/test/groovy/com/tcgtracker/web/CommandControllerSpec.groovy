package com.tcgtracker.web

import com.tcgtracker.command.CommandHandler
import com.tcgtracker.config.SecurityConfig
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Web-slice spec for the write-side controller. Boots only the MVC + Spring Security
 * layer (the real SecurityConfig is imported), so it asserts the JWT contract:
 * unauthenticated writes are rejected, and an authenticated write acts as the token's
 * subject — never a client-supplied user id.
 */
@WebMvcTest(CommandController)
@Import(SecurityConfig)
class CommandControllerSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    @SpringBean
    CommandHandler handler = Mock()

    // SecurityConfig configures an OAuth2 resource server, so the slice needs a JwtDecoder bean to
    // build the filter chain. No real decoding happens — the jwt() post-processor injects the auth.
    @SpringBean
    JwtDecoder jwtDecoder = Mock()

    def "an unauthenticated write command is rejected with 401"() {
        when:
        def result = mockMvc.perform(post("/api/commands/add-copy")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"pokewalletId":"pw-1","condition":"Near Mint"}'))

        then: "rejected at the filter chain, so the handler is never reached"
        result.andExpect(status().isUnauthorized())
        0 * handler.addCopy(_, _, _)
    }

    def "an authenticated add-copy acts as the JWT subject, not a client-supplied id"() {
        when:
        def result = mockMvc.perform(post("/api/commands/add-copy")
                .with(jwt().jwt({ builder -> builder.subject("user-123") }))
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"pokewalletId":"pw-1","condition":"Near Mint"}'))

        then: "the controller passes the token subject through to the command handler"
        1 * handler.addCopy("user-123", "pw-1", "Near Mint") >> "col-1"
        result.andExpect(status().isOk())
    }

    def "add-copy returns 422 when the card is not in the catalog"() {
        when:
        def result = mockMvc.perform(post("/api/commands/add-copy")
                .with(jwt().jwt({ builder -> builder.subject("user-123") }))
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"pokewalletId":"missing","condition":"Near Mint"}'))

        then:
        1 * handler.addCopy("user-123", "missing", "Near Mint") >> null
        result.andExpect(status().isUnprocessableEntity())
    }
}
