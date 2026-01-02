package com.multitenant.saas.config

import zio.Config.Secret
import eu.timepit.refined.types.net.PortNumber
import eu.timepit.refined.types.string.NonEmptyString
import zio.ZLayer

//1. Adding dummy objects so the app compiles and does not connect to  real env or server.
//In this first push I will use Zio for easy set up, once this is the foundation for next changes I will rever Zio to Ciris
//TODO: Change Zio for Ciris project to hold secrets.
final case class DatabaseConfig(
                                 host: NonEmptyString,
                                 port: PortNumber,
                                 name: NonEmptyString,
                                 user: NonEmptyString,
                                 password: Secret,
                                 maxConnections: Int
                               )
final case class RedisConfig(
                              host: String = "localhost",
                              port: Int = 6379
                            )

final case class HttpConfig(
                             host: String = "0.0.0.0",
                             port: Int = 8080
                           )

enum Environment:
  case Development, Staging, Production

final case class AppConfig(
                            database: DatabaseConfig,
                            redis: RedisConfig,
                            http: HttpConfig,
                            environment: Environment
                          )



object DatabaseConfig:

  val dummy: DatabaseConfig =
    DatabaseConfig(
      host = NonEmptyString.unsafeFrom("localhost"),
      port = PortNumber.unsafeFrom(5432),
      name = NonEmptyString.unsafeFrom("dummy_db"),
      user = NonEmptyString.unsafeFrom("dummy_user"),
      password = Secret("dummy_password"),
      maxConnections = 1
    )

object AppConfig:

  import zio._

  val dummy: AppConfig =
    AppConfig(
      database = DatabaseConfig.dummy,
      redis = RedisConfig(),
      http = HttpConfig(),
      environment = Environment.Development
    )

  val layer: ZLayer[Any, Nothing, AppConfig] =
    ZLayer.succeed(dummy)