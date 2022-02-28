package com.github.al.assad.akka.Http.d_swagger

import com.github.swagger.akka.SwaggerHttpService


class SwaggerDocService extends SwaggerHttpService {
  override def apiClasses: Set[Class[_]] = Set(classOf[HelloService], classOf[EchoService])


}
