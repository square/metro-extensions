package com.squareup.di

fun interface GraphFactoryProvider<FactoryT : Any> {
  fun provideGraphFactory(): FactoryT
}
