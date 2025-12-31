package com.multitenant.saas

import zio.*
import zio.http.*

object Main extends ZIOAppDefault:

  val healthRoutes: Routes[Any, Response] =
    Routes(
      Method.GET / "health" -> handler {
        Response.json("""{"status":"ok","version":"0.1.0"}""")
      },
      Method.GET / "ready" -> handler {
        Response.json("""{"status":"ready"}""")
      }
    )

  val app: HttpApp[Any] =
    healthRoutes.toHttpApp

  override def run: ZIO[Any, Throwable, Unit] =
    for
      _ <- ZIO.logInfo("Starting Multi-Tenant SaaS Platform...")
      _ <- ZIO.logInfo("Server listening on port 8080")
      _ <- Server.serve(app).provide(Server.default)
    yield ()
