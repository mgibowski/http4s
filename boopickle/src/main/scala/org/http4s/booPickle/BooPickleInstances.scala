package org.http4s
package booPickle

import boopickle.Default._
import boopickle.Pickler
import cats.Applicative
import cats.effect.Sync
import fs2.Chunk
import java.nio.ByteBuffer
import org.http4s._
import org.http4s.EntityEncoder.chunkEncoder
import org.http4s.headers.`Content-Type`
import scala.util.{Failure, Success}

/**
  * Generic factories for http4s encoders/decoders for boopickle
  * Note that the media type is set for application/octet-stream
  */
trait BooPickleInstances {
  private def booDecoderByteBuffer[F[_]: Sync, A](m: Media[F])(
      implicit pickler: Pickler[A]): DecodeResult[F, A] =
    EntityDecoder.collectBinary(m).subflatMap { chunk =>
      val bb = ByteBuffer.wrap(chunk.toArray)
      if (bb.hasRemaining) {
        Unpickle[A](pickler).tryFromBytes(bb) match {
          case Success(bb) => Right(bb)
          case Failure(pf) => Left(MalformedMessageBodyFailure("Invalid binary body", Some(pf)))
        }
      } else
        Left(MalformedMessageBodyFailure("Invalid binary: empty body", None))
    }

  /**
    * Create an `EntityDecoder` for `A` given a `Pickler[A]`
    */
  def booOf[F[_]: Sync, A: Pickler]: EntityDecoder[F, A] =
    EntityDecoder.decodeBy(MediaType.application.`octet-stream`)(booDecoderByteBuffer[F, A])

  /**
    * Create an `EntityEncoder` for `A` given a `Pickler[A]`
    */
  def booEncoderOf[F[_]: Applicative, A: Pickler]: EntityEncoder[F, A] =
    chunkEncoder[F]
      .contramap[A] { v =>
        Chunk.ByteBuffer(Pickle.intoBytes(v))
      }
      .withContentType(`Content-Type`(MediaType.application.`octet-stream`))
}
