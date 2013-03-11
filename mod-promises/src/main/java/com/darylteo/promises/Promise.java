package com.darylteo.promises;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.util.AtomicObservableSubscription;
import rx.util.functions.Func1;
import rx.util.functions.Functions;

/**
 * A Promise represents a request that will be fulfilled sometime in the future,
 * most usually by an asynchrous task executed on the Vert.x Event Loop. It
 * allows you to assign handlers to deal with the return results of asynchronus
 * tasks, and to flatten "pyramids of doom" or "callback hell".
 * 
 * @author Daryl Teo
 * 
 * @param T
 *          - the data type of the result contained by this Promise.
 */
public class Promise<T> extends Observable<T> {
  private static enum STATE {
    PENDING,
    FULFILLED,
    REJECTED
  }

  private Promise<T> that = this;

  private T value = null;
  private Exception reason;

  private STATE state = STATE.PENDING;

  // private CompletedHandler<Void> onFinally = null;
  // private CompletedHandler<T> onFulfilled = null;
  // private CompletedHandler<Throwable> onRejected = null;

  private Map<Subscription, Observer<T>> observers;

  public static <T> Promise<T> defer() {
    // LinkedHashMap used to preserve key insertion order
    final Map<Subscription, Observer<T>> observers = new LinkedHashMap<Subscription, Observer<T>>();

    final Promise<T> promise = new Promise<>(
        new Func1<Observer<T>, Subscription>() {
          @Override
          public Subscription call(Observer<T> observer) {
            final AtomicObservableSubscription subscription = new AtomicObservableSubscription();

            subscription.wrap(new Subscription() {
              @Override
              public void unsubscribe() {
                // on unsubscribe remove it from the map of outbound observers
                // to
                // notify
                observers.remove(subscription);
              }
            });

            observers.put(subscription, observer);
            return subscription;
          }
        },
        observers
        );

    return promise;
  }

  protected Promise(Func1<Observer<T>, Subscription> onSubscribe, Map<Subscription, Observer<T>> observers) {
    super(onSubscribe);
    this.observers = observers;
  }

  /* Defer Methods */
  public <O> Promise<O> then(Object onFulfilled) {
    return this._then(onFulfilled, null, null);
  }

  public <O> Promise<O> then(Object onFulfilled, Object onRejected) {
    return this._then(onFulfilled, onRejected, null);
  }

  public <O> Promise<O> fail(Object onRejected) {
    return this._then(null, onRejected, null);
  }

  public Promise<T> fin(Object onFinally) {
    return this._then(null, null, onFinally);
  }

  private <O> Promise<O> _then(
      final Object onFulfilled,
      final Object onRejected,
      final Object onFinally)
  {
    final Promise<O> promise = Promise.defer();

    // Create the Observer
    Observer<T> observer = new Observer<T>() {
      private T value = null;

      @Override
      public void onCompleted() {
        Object result = null;

        try {
          this.callFinally();

          if (onFulfilled == null) {
            // We don't have a handler so we'll just forward on
            // We have to assume that the casting will work...
            promise.fulfill((O) this.value);
            return;
          } else {
            // if fin() is called, then onFulfilled would be null
            result = Functions.from(onFulfilled).call(this.value);
          }
        } catch (Exception e) {
          result = e;
        }

        this.evalResult(result);
      }

      @Override
      @SuppressWarnings("unchecked")
      public void onError(Exception reason) {
        Object result = null;

        try {
          this.callFinally();

          if (onRejected == null) {
            // We don't have a handler so we'll just forward on
            // We have to assume that the casting will work...
            promise.reject(reason);
            return;
          } else {
            result = Functions.from(onRejected).call(reason);
          }
        } catch (Exception e) {
          result = e;
        }

        this.evalResult(result);
      }

      @Override
      public void onNext(T value) {
        this.value = value;
      }

      private Object callFinally() {
        Object result = null;
        if (onFinally != null) {
          result = Functions.from(onFinally).call();
          System.out.println(result);
          // results from fin() handler is ignored
          if (!(result instanceof Promise)) {
            result = null;
          }
        }

        return result;
      }

      @SuppressWarnings("unchecked")
      private void evalResult(Object result) {
        if (result instanceof Promise) {
          ((Promise<O>) result).become(promise);
        } else if (result instanceof Exception) {
          promise.reject((Exception) result);
        } else {
          promise.fulfill((O) result);
        }
      }
    };

    this.subscribe(observer);

    // Immediately notify observer if result of this promise has already been
    // determined
    if (this.state == STATE.FULFILLED) {
      observer.onNext(this.value);
      observer.onCompleted();
    } else if (this.state == STATE.REJECTED) {
      observer.onError(this.reason);
    }

    return promise;
  }

  /* Result Methods */
  public void fulfill(T value) {
    if (this.state != STATE.PENDING) {
      throw new IllegalStateException();
    }

    this.state = STATE.FULFILLED;
    this.value = value;

    // A copy of the observers is taken first, in case more observers are added
    // after.
    List<Observer<T>> observerList = new ArrayList<>(this.observers.values());
    for (Observer<T> obs : observerList) {
      obs.onNext(this.value);
      obs.onCompleted();
    }
  }

  public void reject(Exception reason) {
    if (this.state != STATE.PENDING) {
      throw new IllegalStateException();
    }

    this.state = STATE.REJECTED;
    this.reason = reason;

    List<Observer<T>> observerList = new ArrayList<>(this.observers.values());
    for (Observer<T> obs : observerList) {
      obs.onError(this.reason);
    }
  }

  public void become(Promise<T> other) {
    this.observers.putAll(other.observers);
  }
}
