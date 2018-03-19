/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

public final class LLVMNativeFunctions {

    private final NFIContextExtension nfiContext;
    private final Map<String, TruffleObject> nativeFunctions;

    public LLVMNativeFunctions(NFIContextExtension nfiContext) {
        this.nfiContext = nfiContext;
        this.nativeFunctions = new HashMap<>();
    }

    private TruffleObject getNativeFunction(LLVMContext context, String name, String signature) {
        return nativeFunctions.computeIfAbsent(name, s -> nfiContext.getNativeFunction(context, name, signature));
    }

    public NullPointerNode createNullPointerNode(LLVMContext context) {
        TruffleObject nullPointerFunction = getNativeFunction(context, "@getNullPointer", "():POINTER");
        return new NullPointerNode(nullPointerFunction);
    }

    public DynamicCastNode createDynamicCast(LLVMContext context) {
        TruffleObject dynamicCastFunction = getNativeFunction(context, "@__dynamic_cast", "(POINTER,POINTER,POINTER,UINT64):POINTER");
        return new DynamicCastNode(dynamicCastFunction);
    }

    public SulongFreeExceptionNode createFreeException(LLVMContext context) {
        TruffleObject freeFunction = getNativeFunction(context, "@__cxa_free_exception", "(POINTER):VOID");
        return new SulongFreeExceptionNode(freeFunction);
    }

    protected abstract static class HeapFunctionNode extends Node {

        private final TruffleObject function;
        @Child private Node nativeExecute;

        protected HeapFunctionNode(TruffleObject function, int argCount) {
            this.function = function;
            this.nativeExecute = Message.createExecute(argCount).createNode();
        }

        protected Object execute(Object... args) {
            try {
                return ForeignAccess.sendExecute(nativeExecute, function, args);
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        }
    }

    public static final class SulongFreeExceptionNode extends HeapFunctionNode {
        private SulongFreeExceptionNode(TruffleObject function) {
            super(function, 1);
        }

        public void free(LLVMAddress ptr) {
            execute(ptr.getVal());
        }
    }

    public static final class DynamicCastNode extends HeapFunctionNode {

        @Child private Node asPointer = Message.AS_POINTER.createNode();

        private DynamicCastNode(TruffleObject function) {
            super(function, 4);
        }

        public LLVMAddress execute(LLVMAddress object, LLVMAddress type1, LLVMAddress type2, long value) {
            try {
                return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, (TruffleObject) execute(object.getVal(), type1.getVal(), type2.getVal(), value)));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public static final class NullPointerNode extends HeapFunctionNode {

        private NullPointerNode(TruffleObject function) {
            super(function, 0);
        }

        public TruffleObject getNullPointer() {
            return (TruffleObject) execute();
        }
    }
}
