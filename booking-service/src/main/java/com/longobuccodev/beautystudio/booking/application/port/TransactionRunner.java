package com.longobuccodev.beautystudio.booking.application.port;

import java.util.function.Supplier;

/** Executa o trecho numa transação: mudança de dados e gravação do evento ficam juntas ou não ficam. */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
