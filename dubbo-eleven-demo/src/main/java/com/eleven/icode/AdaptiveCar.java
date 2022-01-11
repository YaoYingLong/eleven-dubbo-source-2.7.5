package com.eleven.icode;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.ExtensionFactory;

@Adaptive
public class AdaptiveCar implements Car {

    public Car getExtension(Class<Car> type, String name) {
        return new RedCar();
    }

    @Override
    public String getCarName(URL url) {
        return "这是一个Adaptive标注的类";
    }

    @Override
    public String sayHell() {
        return null;
    }
}
