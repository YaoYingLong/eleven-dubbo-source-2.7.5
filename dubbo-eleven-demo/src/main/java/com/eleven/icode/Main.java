package com.eleven.icode;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(value = "com.eleven.icode")
public class Main {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(Main.class);
        RedCar redCar = applicationContext.getBean("redCar", RedCar.class);
        redCar.sayHell();
        System.out.println(redCar);

    }
}
