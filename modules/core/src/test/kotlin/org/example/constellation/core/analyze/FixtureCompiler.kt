package org.example.constellation.core.analyze

import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import kotlin.io.path.writeText

/** Compiles inline Java fixtures with the system javac (`--release 17`) into a temp output dir. */
object FixtureCompiler {

    class Result(val srcDir: Path, val outDir: Path)

    /** [outRelative] controls the output layout — e.g. `mymodule/build/classes` for module-detection tests. */
    fun compile(
        base: Path,
        sources: Map<String, String>,
        outRelative: String = "out",
        classpath: List<Path> = emptyList(),
    ): Result {
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("system Java compiler unavailable")
        val srcDir = base.resolve("src")
        val outDir = base.resolve(outRelative)
        val files = sources.map { (rel, text) ->
            val f = srcDir.resolve(rel)
            Files.createDirectories(f.parent)
            f.writeText(text)
            f
        }
        val args = mutableListOf("--release", "17", "-d", outDir.toString())
        if (classpath.isNotEmpty()) {
            args += listOf("-classpath", classpath.joinToString(":") { it.toString() })
        }
        val rc = compiler.run(null, null, null, *(args + files.map { it.toString() }).toTypedArray())
        require(rc == 0) { "javac failed with rc=$rc" }
        return Result(srcDir, outDir)
    }
}

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
)
