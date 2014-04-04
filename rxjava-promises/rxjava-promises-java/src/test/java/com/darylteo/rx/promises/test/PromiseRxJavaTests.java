package com.darylteo.rx.promises.test;

import com.darylteo.rx.promises.java.Promise;
import org.junit.Test;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

public class PromiseRxJavaTests {

  @Test
  public void testPromiseToRxSubscribe() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final Result<String> result = new Result<String>();

    makePromise("Hello World")
      .toObservable()
      .subscribe(new Action1<String>() {
        @Override
        public void call(String value) {
          result.value = value;
          latch.countDown();
        }
      });

    latch.await();
    assertEquals("Hello World", result.value);
  }

  @Test
  public void testPromiseToRxMap() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final Result<String> result = new Result<String>();

    makePromise("Hello World")
      .toObservable()
      .map(new Func1<String, String>() {
        @Override
        public String call(String value) {
          return value.toUpperCase();
        }
      })
      .subscribe(new Action1<String>() {
        @Override
        public void call(String value) {
          result.value = value;
          latch.countDown();
        }
      });

    latch.await();
    assertEquals("HELLO WORLD", result.value);
  }

  @Test
  public void testPromiseSubscribeToObservable() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final Result<String> result = new Result<String>();

    Observable<String> observable = Observable.from(new String[]{
      "This",
      "Is",
      "An",
      "Ex",
      "Parrot"
    });

    Promise<String> promise = new Promise();
    observable.subscribe(promise);

    promise.toObservable().single().subscribe(new Action1<String>() {
      @Override
      public void call(String value) {
        result.value = value;
        latch.countDown();
      }
    });

    latch.await();
    assertEquals("Parrot", result.value);
  }

  public Promise<String> makePromise(final String value) {
    final Promise<String> promise = Promise.defer();

    new Thread() {
      public void run() {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } finally {
          promise.fulfill(value);
        }
      }
    }.start();

    return promise;
  }

  private class Result<T> {
    T value;
  }
}
