package com.apollographql.apollo3.gradle.test


import com.apollographql.apollo3.api.ExecutionContext
import com.apollographql.apollo3.execution.ExecutableSchema
import com.apollographql.apollo3.execution.GraphQLRequest
import com.apollographql.apollo3.execution.GraphQLRequestError
import com.apollographql.apollo3.execution.parsePostGraphQLRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import okio.buffer
import okio.source
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.junit.Assert.assertEquals
import org.junit.Test
import util.TestUtils
import util.TestUtils.withSimpleProject
import util.TestUtils.withTestProject
import java.io.File

class DownloadSchemaTests {
  private val mockServer = MockWebServer()

  private val preIntrospectionResponse = """
  {
    "data": {
      "schema": {
        "__typename": "__Type",
        "fields": []
      },
      "type": {
        "__typename": "__Type",
        "fields": []
      },
      "directive": {
        "__typename": "__Type",
        "fields": []
      },
      "field": {
        "__typename": "__Type",
        "fields": []
      },
      "inputValue": {
        "__typename": "__Type",
        "fields": []
      }
    }
  }
  """.trimIndent()

  private val schemaString1 = """
  {
    "__schema": {
      "queryType": {
        "name": "foo"
      },
      "types": [
        {
          "kind": "OBJECT",
          "name": "UserInfo",
          "description": null,
          "fields": [
            {
              "name": "id",
              "description": null,
              "args": [],
              "type": {
                "kind": "NON_NULL",
                "name": null,
                "ofType": {
                  "kind": "SCALAR",
                  "name": "ID",
                  "ofType": null
                }
              },
              "isDeprecated": false,
              "deprecationReason": null
            }
          ],
          "inputFields": null,
          "interfaces": [
            {
              "kind": "INTERFACE",
              "name": "MyInterface",
              "ofType": null
            }
          ],
          "enumValues": null,
          "possibleTypes": null
        },
        {
          "kind": "INTERFACE",
          "name": "MyInterface",
          "description": null,
          "fields": [
            {
              "name": "id",
              "description": null,
              "args": [],
              "type": {
                "kind": "NON_NULL",
                "name": null,
                "ofType": {
                  "kind": "SCALAR",
                  "name": "ID",
                  "ofType": null
                }
              },
              "isDeprecated": false,
              "deprecationReason": null
            }
          ],
          "inputFields": null,
          "interfaces": [],
          "enumValues": null,
          "possibleTypes": [
            {
              "kind": "OBJECT",
              "name": "UserInfo",
              "ofType": null
            }
          ]
        },
        {
          "kind": "INPUT_OBJECT",
          "name": "DeprecatedInput",
          "description": null,
          "fields": null,
          "inputFields": [
            {
              "name": "deprecatedField",
              "description": "deprecatedField",
              "type": {
                "kind": "SCALAR",
                "name": "String",
                "ofType": null
              },
              "defaultValue": null,
              "isDeprecated": true,
              "deprecationReason": "DeprecatedForTesting"
            }
          ],
          "interfaces": null,
          "enumValues": null,
          "possibleTypes": null
        }
      ]
    }
  }
  """.trimIndent()

  private val schemaString2 = schemaString1.replace("foo", "bar")

  private val apolloConfiguration = """
      apollo {
        service("mock") {
          schemaFile = file("src/main/graphql/com/example/schema.json")
          introspection {
            endpointUrl = "${mockServer.url("/").toUrl()}"
          }
        }
      }
    """.trimIndent()

  @Test
  fun `schema is downloaded correctly`() {
    withSimpleProject(apolloConfiguration = apolloConfiguration) { dir ->
      mockServer.enqueue(MockResponse().setBody(preIntrospectionResponse))
      mockServer.enqueue(MockResponse().setBody(schemaString1))

      TestUtils.executeTask("downloadMockApolloSchemaFromIntrospection", dir)

      assertEquals(schemaString1, File(dir, "src/main/graphql/com/example/schema.json").readText())
    }
  }


  @Test
  fun `download schema is never up-to-date`() {

    withSimpleProject(apolloConfiguration = apolloConfiguration) { dir ->
      val preIntrospectionMockResponse = MockResponse().setBody(preIntrospectionResponse)
      val schemaMockResponse = MockResponse().setBody(schemaString1)
      mockServer.enqueue(preIntrospectionMockResponse)
      mockServer.enqueue(schemaMockResponse)

      var result = TestUtils.executeTask("downloadMockApolloSchemaFromIntrospection", dir)
      assertEquals(TaskOutcome.SUCCESS, result.task(":downloadMockApolloSchemaFromIntrospection")?.outcome)

      mockServer.enqueue(preIntrospectionMockResponse)
      mockServer.enqueue(schemaMockResponse)

      // Since the task does not declare any output, it should never be up-to-date
      result = TestUtils.executeTask("downloadMockApolloSchemaFromIntrospection", dir)
      assertEquals(TaskOutcome.SUCCESS, result.task(":downloadMockApolloSchemaFromIntrospection")?.outcome)

      assertEquals(schemaString1, File(dir, "src/main/graphql/com/example/schema.json").readText())
    }
  }

  @Test
  fun `download schema is never cached`() {

    withSimpleProject(apolloConfiguration = apolloConfiguration) { dir ->
      val buildCacheDir = File(dir, "buildCache")

      File(dir, "settings.gradle").appendText(""" 
        
        // the empty line above is important
        buildCache {
            local {
                directory '${buildCacheDir.absolutePath}'
            }
        }
      """.trimIndent())

      val schemaFile = File(dir, "src/main/graphql/com/example/schema.json")

      val preIntrospectionMockResponse = MockResponse().setBody(preIntrospectionResponse)
      mockServer.enqueue(preIntrospectionMockResponse)
      mockServer.enqueue(MockResponse().setBody(schemaString1))

      TestUtils.executeTask("downloadMockApolloSchemaFromIntrospection", dir, "--build-cache")
      assertEquals(schemaString1, schemaFile.readText())

      mockServer.enqueue(preIntrospectionMockResponse)
      mockServer.enqueue(MockResponse().setBody(schemaString2))

      TestUtils.executeTask("downloadMockApolloSchemaFromIntrospection", dir, "--build-cache")
      assertEquals(schemaString2, schemaFile.readText())
    }
  }

  @Test
  fun `manually downloading a schema is working`() {

    withSimpleProject(apolloConfiguration = "") { dir ->
      mockServer.enqueue(MockResponse().setBody(preIntrospectionResponse))
      mockServer.enqueue(MockResponse().setBody(schemaString1))

      // Tests can run from any working directory.
      // They used to run in `apollo-gradle-plugin` but with Gradle 6.7, they now run in something like
      // /private/var/folders/zh/xlpqxsfn7vx_dhjswsgsps6h0000gp/T/.gradle-test-kit-martin/test-kit-daemon/6.7/
      // We'll use absolute path as arguments for the check to succeed later on
      val schema = File("build/testProject/schema.json")

      TestUtils.executeGradle(dir, "downloadApolloSchema",
          "--schema=${schema.absolutePath}",
          "--endpoint=${mockServer.url("/")}")

      assertEquals(schemaString1, schema.readText())
    }
  }

  @Test
  fun `manually downloading a schema from self signed endpoint is working`() {
    withSimpleProject(apolloConfiguration = "") { dir ->
      mockServer.enqueue(MockResponse().setBody(preIntrospectionResponse))
      mockServer.enqueue(MockResponse().setBody(schemaString1))

      val selfSignedCertificate = HeldCertificate.Builder().build()
      val certs = HandshakeCertificates.Builder().heldCertificate(selfSignedCertificate).build()
      mockServer.useHttps(certs.sslSocketFactory(), tunnelProxy = false)

      val schema = File("build/testProject/schema.json")

      TestUtils.executeGradle(dir, "downloadApolloSchema",
          "--schema=${schema.absolutePath}",
          "--endpoint=${mockServer.url("/")}",
          "--insecure")

      assertEquals(schemaString1, schema.readText())
    }
  }

  @Test
  fun `download a schema from a real server is working`() {
    val executableSchema = ExecutableSchema.Builder()
        .schema("type Query {foo: Int}")
        .build()

    val server = routes("/graphql" bind Method.POST to GraphQLHttpHandler(executableSchema, ExecutionContext.Empty))
        .asServer(Jetty(8001))
        .start()

    val buildResult = withTestProject("downloadIntrospection") {dir ->
      TestUtils.executeGradle(dir, "downloadServiceApolloSchemaFromIntrospection")
    }

    assertEquals(TaskOutcome.SUCCESS, buildResult.task(":downloadServiceApolloSchemaFromIntrospection")?.outcome)

    server.stop()
  }

  class GraphQLHttpHandler(val executableSchema: ExecutableSchema, val executionContext: ExecutionContext) : HttpHandler {
    override fun invoke(request: Request): Response {

      val graphQLRequestResult = when (request.method) {
        org.http4k.core.Method.POST -> request.body.stream.source().buffer().use { it.parsePostGraphQLRequest() }
        else -> error("")
      }

      if (graphQLRequestResult is GraphQLRequestError) {
        return Response(Status.BAD_REQUEST).body(graphQLRequestResult.message)
      }
      graphQLRequestResult as GraphQLRequest

      val response = executableSchema.execute(graphQLRequestResult, executionContext)

      val buffer = Buffer()
      response.serialize(buffer)
      val responseText = buffer.readUtf8()

      return Response(Status.OK)
          .header("content-type", "application/json")
          .body(responseText)
    }
  }
}
