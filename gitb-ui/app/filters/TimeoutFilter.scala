package filters

import play.api.mvc._
import scala.concurrent.{Await, Future}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.duration.FiniteDuration
import config.Configurations
import java.util.concurrent.TimeUnit
import org.slf4j.{LoggerFactory, Logger}

class TimeoutFilter extends Filter{
  private final val logger: Logger = LoggerFactory.getLogger(classOf[TimeoutFilter])

  def apply(next: (RequestHeader) => Future[Result])
           (requestHeader: RequestHeader): Future[Result] = {
    val timeout:Int = Configurations.SERVER_REQUEST_TIMEOUT_IN_SECONDS
    Future{
        Await.result(next(requestHeader), FiniteDuration(timeout, TimeUnit.SECONDS))
    }
  }
}
