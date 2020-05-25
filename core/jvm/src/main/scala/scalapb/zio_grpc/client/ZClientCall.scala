package scalapb.zio_grpc.client

import io.grpc.ClientCall
import io.grpc.ClientCall.Listener
import io.grpc.Metadata
import scalapb.zio_grpc.GIO
import zio.ZIO
import io.grpc.Status

trait ZClientCall[-R, Req, Res] extends Any {
  def start(
      responseListener: Listener[Res],
      headers: Metadata
  ): ZIO[R, Status, Unit]

  def request(numMessages: Int): ZIO[R, Status, Unit]

  def cancel(message: String): ZIO[R, Status, Unit]

  def halfClose(): ZIO[R, Status, Unit]

  def sendMessage(message: Req): ZIO[R, Status, Unit]
}

class ZClientCallImpl[Req, Res](private val call: ClientCall[Req, Res])
    extends AnyVal
    with ZClientCall[Any, Req, Res] {
  def start(responseListener: Listener[Res], headers: Metadata): GIO[Unit] =
    GIO.effect(call.start(responseListener, headers))

  def request(numMessages: Int): GIO[Unit] =
    GIO.effect(call.request(numMessages))

  def cancel(message: String): GIO[Unit] =
    GIO.effect(call.cancel(message, null))

  def halfClose(): GIO[Unit] = GIO.effect(call.halfClose())

  def sendMessage(message: Req): GIO[Unit] =
    GIO.effect(call.sendMessage(message))
}

object ZClientCall {
  def apply[Req, Res](call: ClientCall[Req, Res]): ZClientCall[Any, Req, Res] =
    new ZClientCallImpl(call)

  class ForwardingZClientCall[R, Req, Res, R1 >: R](
      protected val delegate: ZClientCall[R1, Req, Res]
  ) extends ZClientCall[R, Req, Res] {
    override def start(
        responseListener: Listener[Res],
        headers: Metadata
    ): ZIO[R, Status, Unit] = delegate.start(responseListener, headers)

    override def request(numMessages: Int): ZIO[R, Status, Unit] =
      delegate.request(numMessages)

    override def cancel(message: String): ZIO[R, Status, Unit] =
      delegate.cancel(message)

    override def halfClose(): ZIO[R, Status, Unit] = delegate.halfClose()

    override def sendMessage(message: Req): ZIO[R, Status, Unit] =
      delegate.sendMessage(message)
  }

  def headersTransformer[R, Req, Res](
      clientCall: ZClientCall[R, Req, Res],
      fetchHeaders: Metadata => ZIO[R, Status, Metadata]
  ) =
    new ForwardingZClientCall[R, Req, Res, R](clientCall) {
      override def start(
          responseListener: Listener[Res],
          headers: Metadata
      ): ZIO[R, Status, Unit] =
        fetchHeaders(headers) >>= { h => delegate.start(responseListener, h) }
    }
}