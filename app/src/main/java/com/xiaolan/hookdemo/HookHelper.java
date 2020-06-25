package com.xiaolan.hookdemo;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * author : zhangbao
 * date : 2020/6/26 2:38 AM
 * description :
 */
public class HookHelper {

    //
    public static void HookAMS() {
        try {
            final Class<?> aClass = Class.forName("android.app.ActivityManager");
            Field activityTaskManagerSingletonField = aClass.getDeclaredField("IActivityManagerSingleton");
            activityTaskManagerSingletonField.setAccessible(true);
            Object singleton = activityTaskManagerSingletonField.get(null);

            Class<?> singletonClaz = Class.forName("android.util.Singleton");
            Field singletonClazField = singletonClaz.getDeclaredField("mInstance");
            singletonClazField.setAccessible(true);

            //拿到Singleton
            final Object rawActivityManager = singletonClazField.get(singleton);

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class<?> iActivityTaskManager = Class.forName("android.app.IActivityManager");

            Object proxyInstance = Proxy.newProxyInstance(classLoader, new Class[]{iActivityTaskManager}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if ("startActivity".equals(method.getName())) {
                        int index = 0;
                        Intent raw = null;
                        for (int i = 0; i < args.length; i++) {
                            if (args[i] instanceof Intent) {
                                index = i;
                                raw = (Intent) args[i];
                                break;
                            }
                        }

                        Intent newIntent = new Intent();
                        String pgName = "com.xiaolan.hookdemo";
                        newIntent.setComponent(new ComponentName(pgName,StubActivity.class.getName()));
                        newIntent.putExtra("extra",raw);
                        args[index] = newIntent;

                    }
                    return method.invoke(rawActivityManager,args);
                }
            });
            //代理对象还回到framework
            singletonClazField.set(singleton,proxyInstance);

        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static void hookHandler() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Field sCurrentActivityThreadField = atClass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            Object sCurrentActivityThread = sCurrentActivityThreadField.get(null);

            Field mHField = atClass.getDeclaredField("mH");
            mHField.setAccessible(true);
            final Handler handler = (Handler) mHField.get(sCurrentActivityThread);

            final Field mCallbackField = Handler.class.getDeclaredField("mCallback");
            mCallbackField.setAccessible(true);
            mCallbackField.set(handler,new Handler.Callback(){
                @Override
                public boolean handleMessage(@NonNull Message msg) {
                    try {
                        Object obj = msg.obj;
//                        Class<?> aClass = Class.forName("android.app.servertransaction.ClientTransaction");
//                        if (!aClass.isInstance(obj)) return false;
                        Field field = obj.getClass().getDeclaredField("mActivityCallbacks");
                        field.setAccessible(true);
                        List list = (List) field.get(obj);
                        if (list.size() == 0)
                            return false;

                        Object o = list.get(0);
                        Class<?>  item = Class.forName("android.app.servertransaction.LaunchActivityItem");
                        if (item.isInstance(o)) {
                            Field intentField = item.getDeclaredField("mIntent");
                            intentField.setAccessible(true);
                            Intent intent = (Intent) intentField.get(o);
                            Intent targetIntent = intent.getParcelableExtra("extra");
                            intent.setComponent(targetIntent.getComponent());
//                            mCallbackField.set(handler,null);
                        }
                    } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            });
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
