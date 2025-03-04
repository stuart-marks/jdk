/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @run testng/othervm/native --enable-native-access=ALL-UNNAMED TestNative
 */

import java.lang.foreign.*;
import java.lang.foreign.MemoryLayout.PathElement;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.VarHandle;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.testng.Assert.*;

public class TestNative extends NativeTestHelper {

    static SequenceLayout bytes = MemoryLayout.sequenceLayout(100,
            ValueLayout.JAVA_BYTE.withOrder(ByteOrder.nativeOrder())
    );

    static SequenceLayout chars = MemoryLayout.sequenceLayout(100,
            ValueLayout.JAVA_CHAR.withOrder(ByteOrder.nativeOrder())
    );

    static SequenceLayout shorts = MemoryLayout.sequenceLayout(100,
            ValueLayout.JAVA_SHORT.withOrder(ByteOrder.nativeOrder())
    );

    static SequenceLayout ints = MemoryLayout.sequenceLayout(100,
            JAVA_INT.withOrder(ByteOrder.nativeOrder())
    );

    static SequenceLayout floats = MemoryLayout.sequenceLayout(100,
            ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.nativeOrder())
    );

    static SequenceLayout longs = MemoryLayout.sequenceLayout(100,
            ValueLayout.JAVA_LONG.withOrder(ByteOrder.nativeOrder())
    );

    static SequenceLayout doubles = MemoryLayout.sequenceLayout(100,
            ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.nativeOrder())
    );

    static VarHandle byteHandle = bytes.varHandle(PathElement.sequenceElement());
    static VarHandle charHandle = chars.varHandle(PathElement.sequenceElement());
    static VarHandle shortHandle = shorts.varHandle(PathElement.sequenceElement());
    static VarHandle intHandle = ints.varHandle(PathElement.sequenceElement());
    static VarHandle floatHandle = floats.varHandle(PathElement.sequenceElement());
    static VarHandle longHandle = longs.varHandle(PathElement.sequenceElement());
    static VarHandle doubleHandle = doubles.varHandle(PathElement.sequenceElement());

    static void initBytes(MemorySegment base, SequenceLayout seq, BiConsumer<MemorySegment, Long> handleSetter) {
        for (long i = 0; i < seq.elementCount() ; i++) {
            handleSetter.accept(base, i);
        }
    }

    static <Z extends Buffer> void checkBytes(MemorySegment base, SequenceLayout layout,
                                              BiFunction<MemorySegment, Long, Object> handleExtractor,
                                              Function<ByteBuffer, Z> bufferFactory,
                                              BiFunction<Z, Integer, Object> nativeBufferExtractor,
                                              BiFunction<Long, Integer, Object> nativeRawExtractor) {
        long nelems = layout.elementCount();
        ByteBuffer bb = base.asByteBuffer();
        Z z = bufferFactory.apply(bb);
        for (long i = 0 ; i < nelems ; i++) {
            Object handleValue = handleExtractor.apply(base, i);
            Object bufferValue = nativeBufferExtractor.apply(z, (int)i);
            Object rawValue = nativeRawExtractor.apply(base.address(), (int)i);
            if (handleValue instanceof Number) {
                assertEquals(((Number)handleValue).longValue(), i);
                assertEquals(((Number)bufferValue).longValue(), i);
                assertEquals(((Number)rawValue).longValue(), i);
            } else {
                assertEquals((long)(char)handleValue, i);
                assertEquals((long)(char)bufferValue, i);
                assertEquals((long)(char)rawValue, i);
            }
        }
    }

    public static native byte getByteBuffer(ByteBuffer buf, int index);
    public static native char getCharBuffer(CharBuffer buf, int index);
    public static native short getShortBuffer(ShortBuffer buf, int index);
    public static native int getIntBuffer(IntBuffer buf, int index);
    public static native float getFloatBuffer(FloatBuffer buf, int index);
    public static native long getLongBuffer(LongBuffer buf, int index);
    public static native double getDoubleBuffer(DoubleBuffer buf, int index);

    public static native byte getByteRaw(long addr, int index);
    public static native char getCharRaw(long addr, int index);
    public static native short getShortRaw(long addr, int index);
    public static native int getIntRaw(long addr, int index);
    public static native float getFloatRaw(long addr, int index);
    public static native long getLongRaw(long addr, int index);
    public static native double getDoubleRaw(long addr, int index);

    public static native long getCapacity(Buffer buffer);

    @Test(dataProvider="nativeAccessOps")
    public void testNativeAccess(Consumer<MemorySegment> checker, Consumer<MemorySegment> initializer, SequenceLayout seq) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(seq);;
            initializer.accept(segment);
            checker.accept(segment);
        }
    }

    @Test(dataProvider="buffers")
    public void testNativeCapacity(Function<ByteBuffer, Buffer> bufferFunction, int elemSize) {
        int capacity = (int)doubles.byteSize();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(doubles);;
            ByteBuffer bb = segment.asByteBuffer();
            Buffer buf = bufferFunction.apply(bb);
            int expected = capacity / elemSize;
            assertEquals(buf.capacity(), expected);
            assertEquals(getCapacity(buf), expected);
        }
    }

    @Test
    public void testDefaultAccessModes() {
        MemorySegment addr = allocateMemory(12);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment mallocSegment = addr.asSlice(0, 12)
                    .reinterpret(arena, TestNative::freeMemory);
            assertFalse(mallocSegment.isReadOnly());
        }
    }

    @Test
    public void testMallocSegment() {
        MemorySegment addr = allocateMemory(12);
        MemorySegment mallocSegment = null;
        try (Arena arena = Arena.ofConfined()) {
            mallocSegment = addr.asSlice(0, 12)
                    .reinterpret(arena, TestNative::freeMemory);
            assertEquals(mallocSegment.byteSize(), 12);
            //free here
        }
        assertTrue(!mallocSegment.scope().isAlive());
    }

    @Test
    public void testAddressAccess() {
        MemorySegment addr = allocateMemory(4);
        addr.set(JAVA_INT, 0, 42);
        assertEquals(addr.get(JAVA_INT, 0), 42);
        freeMemory(addr);
    }

    @Test
    public void testBadResize() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(4, 1);
            assertThrows(IllegalArgumentException.class, () -> segment.reinterpret(-1));
            assertThrows(IllegalArgumentException.class, () -> segment.reinterpret(-1, Arena.ofAuto(), null));
        }
    }

    static {
        System.loadLibrary("NativeAccess");
    }

    @DataProvider(name = "nativeAccessOps")
    public Object[][] nativeAccessOps() {
        Consumer<MemorySegment> byteInitializer =
                (base) -> initBytes(base, bytes, (addr, pos) -> byteHandle.set(addr, 0L, pos, (byte)(long)pos));
        Consumer<MemorySegment> charInitializer =
                (base) -> initBytes(base, chars, (addr, pos) -> charHandle.set(addr, 0L, pos, (char)(long)pos));
        Consumer<MemorySegment> shortInitializer =
                (base) -> initBytes(base, shorts, (addr, pos) -> shortHandle.set(addr, 0L, pos, (short)(long)pos));
        Consumer<MemorySegment> intInitializer =
                (base) -> initBytes(base, ints, (addr, pos) -> intHandle.set(addr, 0L, pos, (int)(long)pos));
        Consumer<MemorySegment> floatInitializer =
                (base) -> initBytes(base, floats, (addr, pos) -> floatHandle.set(addr, 0L, pos, (float)(long)pos));
        Consumer<MemorySegment> longInitializer =
                (base) -> initBytes(base, longs, (addr, pos) -> longHandle.set(addr, 0L, pos, (long)pos));
        Consumer<MemorySegment> doubleInitializer =
                (base) -> initBytes(base, doubles, (addr, pos) -> doubleHandle.set(addr, 0L, pos, (double)(long)pos));

        Consumer<MemorySegment> byteChecker =
                (base) -> checkBytes(base, bytes, (addr, pos) -> byteHandle.get(addr, 0L, pos), bb -> bb, TestNative::getByteBuffer, TestNative::getByteRaw);
        Consumer<MemorySegment> charChecker =
                (base) -> checkBytes(base, chars, (addr, pos) -> charHandle.get(addr, 0L, pos), ByteBuffer::asCharBuffer, TestNative::getCharBuffer, TestNative::getCharRaw);
        Consumer<MemorySegment> shortChecker =
                (base) -> checkBytes(base, shorts, (addr, pos) -> shortHandle.get(addr, 0L, pos), ByteBuffer::asShortBuffer, TestNative::getShortBuffer, TestNative::getShortRaw);
        Consumer<MemorySegment> intChecker =
                (base) -> checkBytes(base, ints, (addr, pos) -> intHandle.get(addr, 0L, pos), ByteBuffer::asIntBuffer, TestNative::getIntBuffer, TestNative::getIntRaw);
        Consumer<MemorySegment> floatChecker =
                (base) -> checkBytes(base, floats, (addr, pos) -> floatHandle.get(addr, 0L, pos), ByteBuffer::asFloatBuffer, TestNative::getFloatBuffer, TestNative::getFloatRaw);
        Consumer<MemorySegment> longChecker =
                (base) -> checkBytes(base, longs, (addr, pos) -> longHandle.get(addr, 0L, pos), ByteBuffer::asLongBuffer, TestNative::getLongBuffer, TestNative::getLongRaw);
        Consumer<MemorySegment> doubleChecker =
                (base) -> checkBytes(base, doubles, (addr, pos) -> doubleHandle.get(addr, 0L, pos), ByteBuffer::asDoubleBuffer, TestNative::getDoubleBuffer, TestNative::getDoubleRaw);

        return new Object[][]{
                {byteChecker, byteInitializer, bytes},
                {charChecker, charInitializer, chars},
                {shortChecker, shortInitializer, shorts},
                {intChecker, intInitializer, ints},
                {floatChecker, floatInitializer, floats},
                {longChecker, longInitializer, longs},
                {doubleChecker, doubleInitializer, doubles}
        };
    }

    @DataProvider(name = "buffers")
    public Object[][] buffers() {
        return new Object[][] {
                { (Function<ByteBuffer, Buffer>)bb -> bb, 1 },
                { (Function<ByteBuffer, Buffer>)ByteBuffer::asCharBuffer, 2 },
                { (Function<ByteBuffer, Buffer>)ByteBuffer::asShortBuffer, 2 },
                { (Function<ByteBuffer, Buffer>)ByteBuffer::asIntBuffer, 4 },
                { (Function<ByteBuffer, Buffer>)ByteBuffer::asFloatBuffer, 4 },
                { (Function<ByteBuffer, Buffer>)ByteBuffer::asLongBuffer, 8 },
                { (Function<ByteBuffer, Buffer>)ByteBuffer::asDoubleBuffer, 8 },
        };
    }
}
