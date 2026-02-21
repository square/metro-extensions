package com.squareup.api

import java.lang.reflect.Proxy

interface ServiceCreator {
  fun <T : Any> create(service: Class<T>): T

  object NoOp : ServiceCreator {
    override fun <T : Any> create(service: Class<T>): T {
      @Suppress("UNCHECKED_CAST")
      return Proxy.newProxyInstance(service.classLoader, arrayOf(service)) { _, _, _ ->
        throw NotImplementedError("Real service invoked")
      } as T
    }
  }
}
