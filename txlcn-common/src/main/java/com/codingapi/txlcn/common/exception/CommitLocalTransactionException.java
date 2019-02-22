package com.codingapi.txlcn.common.exception;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class CommitLocalTransactionException extends Exception {
	public CommitLocalTransactionException(String message) {
        super(message);
    }

    public CommitLocalTransactionException(Throwable ex) {
        super(ex);
    }
}
