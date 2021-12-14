package com.eleven.icode;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Protocol;

public class SpiTest {
    public static void main(String[] args) {

//        ServiceLoader<Car> cars = ServiceLoader.load(Car.class);
//        for (Car car : cars) {
//            System.out.println(car.getCarName(null));
//        }

//        ExtensionLoader<Protocol> extensionLoader = ExtensionLoader.getExtensionLoader(Protocol.class);
//        Protocol protocol = extensionLoader.getExtension("dubbo");
//        System.out.println(protocol);

//        ExtensionLoader<Car> extensionLoader = ExtensionLoader.getExtensionLoader(Car.class);
//        Car car = extensionLoader.getExtension("red"); // 自动注入，AOP
//        System.out.println(car.getCarName(null));

        ExtensionLoader<Person> extensionLoader = ExtensionLoader.getExtensionLoader(Person.class);
        Person person = extensionLoader.getExtension("black");  // BlackPerson
        URL url = new URL(null, null, -1);
        url = url.addParameter("car", "black");
        System.out.println(person.getCar().getCarName(url));  // 代理逻辑


//        System.out.println(person.getCar().getCarName(url));


//        ExtensionLoader<Filter> extensionLoader = ExtensionLoader.getExtensionLoader(Filter.class);
//        URL url = new URL("http://", "localhost", 8080);
//        url = url.addParameter("cache", "test");
//        List<Filter> activateExtensions = extensionLoader.getActivateExtension(url, new String[]{"validation"}, CommonConstants.CONSUMER);
//        for (Filter activateExtension : activateExtensions) {
//            System.out.println(activateExtension);
//        }


//        ConcurrentHashSet set = new ConcurrentHashSet();
//        set.add("eleven1");
//        set.add("eleven2");
//        set.add("eleven3");
//
//        System.out.println(set);
    }
}
