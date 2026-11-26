package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.cloud.oracle.OracleObjectStorageController
import com.dogGetDrunk.meetjyou.support.EXTERNAL_CALL_H2_URL
import com.dogGetDrunk.meetjyou.support.ExternalCallTestConfig
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.util.UUID

/**
 * AT-004: open-in-view is off exactly for the OCI image routes and the place search, and on for every
 * other handler. Judged per handler, so a pattern that also catches an unrelated route fails here.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=$EXTERNAL_CALL_H2_URL"],
)
@ActiveProfiles("test")
@Import(ExternalCallTestConfig::class)
@MockBean(
    FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class,
)
class OpenInViewExclusionTest : BehaviorSpec() {
    @Autowired private lateinit var handlerMapping: RequestMappingHandlerMapping

    private data class Route(val method: String, val path: String, val handler: HandlerMethod, val openInView: Boolean)

    // Path variables become a UUID, which every mapped variable here accepts.
    private fun samplePath(pattern: String) = pattern.replace(Regex("\\{[^}]+}"), UUID.randomUUID().toString())

    // OpenInViewConfig is the only web-request interceptor, so its adapter tells whether it applies.
    private fun routes(): List<Route> = handlerMapping.handlerMethods.flatMap { (info, _) ->
        val methods = info.methodsCondition.methods.ifEmpty { setOf(RequestMethod.GET) }
        info.patternValues.flatMap { pattern ->
            methods.map { method ->
                val request = MockHttpServletRequest(method.name, samplePath(pattern))
                val chain = handlerMapping.getHandler(request) ?: error("no handler for $method $pattern")
                val openInView = chain.interceptorList.any { it is WebRequestHandlerInterceptorAdapter }
                Route(method.name, pattern, chain.handler as HandlerMethod, openInView)
            }
        }
    }

    private fun isExpectedExclusion(route: Route) =
        route.handler.beanType == OracleObjectStorageController::class.java ||
            route.path == "${ApiVersionConfig.V1}/places/search"

    init {
        extensions(SpringExtension())

        given("모든 핸들러의 open-in-view 적용 여부") {
            then("OCI 이미지 핸들러 12개와 장소 검색만 빠지고 나머지는 전부 유지된다") {
                val routes = routes()
                val wrong = routes.filter { it.openInView == isExpectedExclusion(it) }
                    .map { "${it.method} ${it.path} openInView=${it.openInView}" }

                wrong.shouldBeEmpty()
                val imageRoutes = routes.filter { it.handler.beanType == OracleObjectStorageController::class.java }
                imageRoutes shouldHaveSize IMAGE_ROUTES
            }
        }
    }

    private companion object {
        const val IMAGE_ROUTES = 12
    }
}
