package org.example.constellation.core.analyze

/**
 * One rich fixture set exercising every analyzer feature.
 * Packages under `demo` are analyzed; `extlib` is compiled but kept out of the analysis input.
 */
val RICH_FIXTURE: Map<String, String> = mapOf(
    "demo/model/Point.java" to """
        package demo.model;
        public class Point {
            private int x;
            private int y;
            public int getX() { return x; }
            public void setX(int x) { this.x = x; }
            public int getY() { return y; }
            public void setY(int y) { this.y = y; }
            @Override public boolean equals(Object o) { return o == this; }
            @Override public int hashCode() { return 1; }
            @Override public String toString() { return "pt"; }
        }
    """.trimIndent(),

    "demo/model/Shape.java" to """
        package demo.model;
        public interface Shape {
            double area();
        }
    """.trimIndent(),

    "demo/model/Resizable.java" to """
        package demo.model;
        public interface Resizable {
            void resize(double factor);
        }
    """.trimIndent(),

    "demo/model/Circle.java" to """
        package demo.model;
        public class Circle implements Shape, Resizable {
            private double r;
            public double getR() { return r; }
            public double area() { return 3.14 * r * r; }
            public void resize(double factor) { r = r * factor; }
        }
    """.trimIndent(),

    "demo/model/Color.java" to """
        package demo.model;
        public enum Color {
            RED, GREEN
        }
    """.trimIndent(),

    "demo/model/Money.java" to """
        package demo.model;
        public record Money(int amount, String currency) {
        }
    """.trimIndent(),

    "demo/anno/Label.java" to """
        package demo.anno;
        public @interface Label {
            String value();
        }
    """.trimIndent(),

    "demo/chain/A.java" to """
        package demo.chain;
        public class A extends B {
            public void a() { }
        }
    """.trimIndent(),

    "demo/chain/B.java" to """
        package demo.chain;
        public class B extends C {
            public void b() { }
        }
    """.trimIndent(),

    "demo/chain/C.java" to """
        package demo.chain;
        public class C {
            public void c() { }
        }
    """.trimIndent(),

    "demo/iface/IA.java" to """
        package demo.iface;
        public interface IA {
            void go();
        }
    """.trimIndent(),

    "demo/iface/IB.java" to """
        package demo.iface;
        public interface IB extends IA {
            void stop();
        }
    """.trimIndent(),

    "demo/animal/Animal.java" to """
        package demo.animal;
        public class Animal {
            public void eat() { }
        }
    """.trimIndent(),

    "demo/animal/Dog.java" to """
        package demo.animal;
        public class Dog extends Animal {
            public void bark() { }
        }
    """.trimIndent(),

    "demo/animal/DogService.java" to """
        package demo.animal;
        public class DogService {
            public void walk(Dog d) {
                d.bark();
                d.bark();
                d.eat();
                Dog puppy = new Dog();
                puppy.bark();
                Animal a = new Animal();
                a.eat();
                new extlib.Util().go();
                String.valueOf(1);
            }
            public void twice() { walk(new Dog()); }
            public void anon() {
                Runnable r = new Runnable() {
                    @Override public void run() { new extlib.Side().ping(); }
                };
                r.run();
            }
        }
    """.trimIndent(),

    "demo/nested/Outer.java" to """
        package demo.nested;
        public class Outer {
            public static class Inner {
                public void ping() { }
            }
            public void use() {
                new Inner().ping();
            }
        }
    """.trimIndent(),

    "demo/helper/Helper.java" to """
        package demo.helper;
        public class Helper {
            public String name() { return "h"; }
        }
    """.trimIndent(),

    "demo/ref/Ref.java" to """
        package demo.ref;
        import demo.helper.Helper;
        import java.util.function.Supplier;
        public class Ref {
            public void use(Helper h) {
                Supplier<String> s = h::name;
                s.get();
            }
        }
    """.trimIndent(),

    "demo/pojo/BusinessDto.java" to """
        package demo.pojo;
        public class BusinessDto {
            private int a;
            public int getA() { return a; }
            public void setA(int a) { this.a = a; }
            public int compute() { return a * 2; }
        }
    """.trimIndent(),

    "demo/pojo/DtoWithStatic.java" to """
        package demo.pojo;
        public class DtoWithStatic {
            private int a;
            public int getA() { return a; }
            public static DtoWithStatic of(int a) { return new DtoWithStatic(); }
        }
    """.trimIndent(),

    "demo/pojo/GhostGetter.java" to """
        package demo.pojo;
        public class GhostGetter {
            private int x;
            public int getX() { return x; }
            public int getZ() { return 0; }
        }
    """.trimIndent(),

    "extlib/Util.java" to """
        package extlib;
        public class Util {
            public void go() { }
        }
    """.trimIndent(),

    /** Referenced only from inside DogService's anonymous class body. */
    "extlib/Side.java" to """
        package extlib;
        public class Side {
            public void ping() { }
        }
    """.trimIndent(),

    /** Bean with reference-typed setters — regression guard for descriptor param counting. */
    "demo/model/User.java" to """
        package demo.model;
        public class User {
            private String name;
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
        }
    """.trimIndent(),

    /** Bean with a business-named copy() but no componentN — NOT a Kotlin data class. */
    "demo/model/Report.java" to """
        package demo.model;
        public class Report {
            private int size;
            public int getSize() { return size; }
            public void setSize(int size) { this.size = size; }
            public Report copy() { return new Report(); }
        }
    """.trimIndent(),

    /** Two overloads differing only by the parameter's package. */
    "demo/alpha/Mark.java" to """
        package demo.alpha;
        public class Mark { }
    """.trimIndent(),

    "demo/beta/Mark.java" to """
        package demo.beta;
        public class Mark { }
    """.trimIndent(),

    "demo/pick/Picker.java" to """
        package demo.pick;
        public class Picker {
            public String pick(demo.alpha.Mark m) { return "a"; }
            public String pick(demo.beta.Mark m) { return "b"; }
        }
    """.trimIndent(),

    "demo/pick/PickerUser.java" to """
        package demo.pick;
        public class PickerUser {
            public void use(Picker p) {
                p.pick(new demo.alpha.Mark());
                p.pick(new demo.beta.Mark());
            }
        }
    """.trimIndent(),

    /** Static components: singleton, static nested, inner, static holders. */
    "demo/stat/Singleton.java" to """
        package demo.stat;
        public class Singleton {
            public static final Singleton INSTANCE = new Singleton();
            private Singleton() { }
            public String name() { return "s"; }
        }
    """.trimIndent(),

    "demo/stat/Outer.java" to """
        package demo.stat;
        public class Outer {
            public static class Util { }
            public class Inner { }
        }
    """.trimIndent(),

    "demo/stat/FileUtils.java" to """
        package demo.stat;
        public class FileUtils {
            public static String ext(String name) { return ""; }
        }
    """.trimIndent(),

    "demo/stat/Counter.java" to """
        package demo.stat;
        public class Counter {
            private static int count;
            public static int next() { return ++count; }
        }
    """.trimIndent(),

    /** Not a singleton: INSTANCE holds someone else's type; has instance behavior. */
    "demo/stat/ClockHolder.java" to """
        package demo.stat;
        public class ClockHolder {
            public static final Singleton INSTANCE = Singleton.INSTANCE;
            public long now() { return 1L; }
        }
    """.trimIndent(),

    /** Constants-only class: static content without any static methods. */
    "demo/stat/Consts.java" to """
        package demo.stat;
        public class Consts {
            public static final String NAME = "demo";
            public static final int LIMIT = 10;
        }
    """.trimIndent(),

    /** Nested record: implicitly static in bytecode, but a data carrier, not a static component. */
    "demo/stat/Wrapper.java" to """
        package demo.stat;
        public class Wrapper {
            public record P(int x) { }
        }
    """.trimIndent(),

    /** Subclass of a stateful superclass: state is inherited, so not a static holder. */
    "demo/app/AppException.java" to """
        package demo.app;
        public class AppException extends RuntimeException {
            public AppException(String message) { super(message); }
            public static AppException wrap(Throwable t) { return new AppException(String.valueOf(t)); }
        }
    """.trimIndent(),

    /** Static vs instance dispatch: invokestatic, method references to statics, constructor calls. */
    "demo/stat/StatCaller.java" to """
        package demo.stat;
        import demo.app.AppException;
        import java.util.function.Function;
        public class StatCaller {
            public void run() {
                FileUtils.ext("a.txt");
                Counter.next();
                AppException.wrap(new RuntimeException("x"));
                RuntimeException direct = new RuntimeException("y");
                AppException wrapped = new AppException(direct.getMessage());
                Function<String, String> ref = FileUtils::ext;
                ref.apply("b.txt");
                if (wrapped.getMessage().isEmpty()) { throw wrapped; }
            }
        }
    """.trimIndent(),
)
