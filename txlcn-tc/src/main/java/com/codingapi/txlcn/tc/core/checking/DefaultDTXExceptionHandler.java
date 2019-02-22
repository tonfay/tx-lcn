/*
 * Copyright 2017-2019 CodingApi .
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.codingapi.txlcn.tc.core.checking;

import com.codingapi.txlcn.common.exception.*;
import com.codingapi.txlcn.common.util.Transactions;
import com.codingapi.txlcn.logger.TxLogger;
import com.codingapi.txlcn.tc.txmsg.TxMangerReporter;
import com.codingapi.txlcn.tc.core.template.TransactionCleanTemplate;
import com.codingapi.txlcn.txmsg.params.TxExceptionParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Description: 分布式事务异常处理器类
 * Date: 2018/12/20
 *
 * @author ujued
 * @see DTXExceptionHandler
 */
@Component
@Slf4j
public class DefaultDTXExceptionHandler implements DTXExceptionHandler {

    private final TransactionCleanTemplate transactionCleanTemplate;

    private final TxMangerReporter txMangerReporter;

    private final TxLogger txLogger;

    @Autowired
    public DefaultDTXExceptionHandler(TransactionCleanTemplate transactionCleanTemplate,
                                      TxMangerReporter txMangerReporter, TxLogger txLogger) {
        this.transactionCleanTemplate = transactionCleanTemplate;
        this.txMangerReporter = txMangerReporter;
        this.txLogger = txLogger;
    }

    @Override
    public void handleCreateGroupBusinessException(Object params, Throwable ex) throws TransactionException {
        throw new TransactionException(ex);
    }

    @Override
    public void handleCreateGroupMessageException(Object params, Throwable ex) throws TransactionException {
        throw new TransactionException(ex);
    }

    @Override
    public void handleJoinGroupBusinessException(Object params, Throwable ex) throws TransactionException {
        List paramList = (List) params;
        String groupId = (String) paramList.get(0);
        String unitId = (String) paramList.get(1);
        String unitType = (String) paramList.get(2);
        try {
            transactionCleanTemplate.clean(groupId, unitId, unitType, 0);
        } catch (TransactionClearException e) {
            log.error("{} > clean transaction error.", unitType);
            txLogger.error(this.getClass().getSimpleName(), "clean transaction fail.");
        }  catch (CommitLocalTransactionException e) {
        	log.error("{} > clean transaction error.", unitType);
            txLogger.error(this.getClass().getSimpleName(), "clean transaction fail.");
		}
        throw new TransactionException(ex);
    }

    @Override
    public void handleJoinGroupMessageException(Object params, Throwable ex) throws TransactionException {
        throw new TransactionException(ex);
    }

    @Override
    public void handleNotifyGroupBusinessException(Object params, Throwable ex) {
        List paramList = (List) params;
        String groupId = (String) paramList.get(0);
        int state = (int) paramList.get(1);
        String unitId = (String) paramList.get(2);
        String transactionType = (String) paramList.get(3);

        //用户强制回滚.
        if (ex instanceof UserRollbackException) {
            state = 0;
        }
        if ((ex.getCause() != null && ex.getCause() instanceof UserRollbackException)) {
            state = 0;
        }

        // 结束事务
        try {
            transactionCleanTemplate.clean(groupId, unitId, transactionType, state);
        } catch (TransactionClearException e) {
            log.error("{} > clean transaction error.", transactionType);
        } catch (CommitLocalTransactionException e) {
        	log.error("{} > clean transaction error.", transactionType);
		}
    }

    @Override
    public void handleNotifyGroupMessageException(Object params, Throwable ex) {
        // 当0 时候
        List paramList = (List) params;
        String groupId = (String) paramList.get(0);
        int state = (int) paramList.get(1);
        if (state == 0) {
            handleNotifyGroupBusinessException(params, ex);
            return;
        }

        // 按状态正常结束事务（切面补偿记录将保留）
        // TxManager 存在请求异常或者响应异常两种情况。当请求异常时这里的业务需要补偿，当响应异常的时候需要做状态的事务清理。
        // 请求异常时
        //     参与放会根据上报补偿记录做事务的提交。
        // 响应异常时
        //     参与反会正常提交事务，本地业务提示事务。

        // 该两种情况下补偿信息均可以忽略,可直接把本地补偿记录数据删除。


        String unitId = (String) paramList.get(2);
        String transactionType = (String) paramList.get(3);
        try {
            transactionCleanTemplate.compensationClean(groupId, unitId, transactionType, state);
        } catch (TransactionClearException e) {
            log.error("{} > compensationClean transaction error.", transactionType);
        } catch (CommitLocalTransactionException e) {
        	log.error("{} > compensationClean transaction error.", transactionType);
		}

        // 上报Manager，上报直到成功.
        txMangerReporter.reportTransactionState(groupId, null, TxExceptionParams.NOTIFY_GROUP_ERROR, state);
    }
    
    @Override
    public void commitLocalTXException(Object params, Throwable ex) {
    	// 当0 时候
        List paramList = (List) params;
        String groupId = (String) paramList.get(0);
        int state = (int) paramList.get(1);
        if (state == 0) {
            handleNotifyGroupBusinessException(params, ex);
            return;
        }
        String unitId = (String) paramList.get(2);
        String transactionType = (String) paramList.get(3);
     // 上报Manager，上报直到成功.
        Short p = 4;//发起方本地事务异常
        txMangerReporter.reportTransactionState(groupId, null, p, state);
    }
}
