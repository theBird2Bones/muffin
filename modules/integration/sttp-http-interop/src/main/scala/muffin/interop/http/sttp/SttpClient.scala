package muffin.interop.http.sttp

import cats.MonadThrow
import cats.effect.Sync
import cats.syntax.all.given

import sttp.client3.*
import sttp.model.{Method as SMethod, Uri}

import muffin.codec.*
import muffin.error.MuffinError
import muffin.http.*
import muffin.internal.syntax.*

class SttpClient[F[_]: MonadThrow, To[_], From[_]](backend: SttpBackend[F, Any], codec: CodecSupport[To, From])
  extends HttpClient[F, To, From] {

  import codec.given

  def request[In: To, Out: From](
      url: String,
      method: Method,
      body: Body[In],
      headers: Map[String, String],
      params: Params => Params
  ): F[Out] = {
    val req = basicRequest
      .method(
        method match {
          case Method.Get    => SMethod.GET
          case Method.Post   => SMethod.POST
          case Method.Put    => SMethod.PUT
          case Method.Delete => SMethod.DELETE
          case Method.Patch  => SMethod.PATCH
        },
        Uri.unsafeParse(url + params(Params.Empty).mkString)
      )
      .headers(headers)
      .tap {
        req =>
          body match {
            case Body.Empty            => req
            case Body.Json(value)      =>
              req
                .body(Encode[In].apply(value), "UTF-8")
                .header("Content-Type", "application/json")
            case Body.RawJson(value)   =>
              req
                .body(value, "UTF-8")
                .header("Content-Type", "application/json")
            case Body.Multipart(parts) =>
              req
                .multipartBody(
                  parts.map {
                    case MultipartElement.StringElement(name, value) => multipart(name, value)
                    case MultipartElement.FileElement(name, value)   => multipart(name, value)
                  }
                )
                .header("Content-Type", "multipart/form-data")
          }
      }
      .response(asString.mapLeft(MuffinError.Http.apply))
      .mapResponse(_.map{
        asdasdasd => println(asdasdasd)
          asdasdasd
      }.flatMap(Decode[Out].apply))

    backend.send(req)
      .map(_.body)
      .flatMap {
        case Left(error)  => MonadThrow[F].raiseError(error)
        case Right(value) => value.pure[F]
      }
  }

}

object SttpClient {

  def apply[I[_]: Sync, F[_]: MonadThrow, To[_], From[_]](
      backend: SttpBackend[F, Any],
      codec: CodecSupport[To, From]
  ): I[SttpClient[F, To, From]] = Sync[I].delay(new SttpClient[F, To, From](backend, codec))

}
