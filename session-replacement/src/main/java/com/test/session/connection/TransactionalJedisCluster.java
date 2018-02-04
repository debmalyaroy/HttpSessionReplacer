package com.test.session.connection;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import com.test.session.connection.api.RedisConnector.ResponseFacade;
import com.test.session.connection.api.RedisConnector.TransactionFacade;
import com.test.session.connection.api.RedisConnector.TransactionRunner;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisClusterCommand;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Transaction;

/**
 * Extension of Redis cluster interface that supports transactions.
 */
public class TransactionalJedisCluster extends JedisCluster {

    public TransactionalJedisCluster(Set<HostAndPort> hostAndPort, int timeout, JedisPoolConfig config) {
        super(hostAndPort, timeout, config);
    }

    /**
     * Implementation of pseudo-transaction functionality on redis cluster.
     * Transactions are not executed in atomic way, but rother one command at
     * the time.
     * 
     * @param transaction
     *            the sequence of redis commands to run
     * @return result of transaction
     */
    public <T> ResponseFacade<T> transaction(final TransactionRunner<T> transaction) {
        TransactionAsSequence t = new TransactionAsSequence(this);
        ResponseFacade<T> response = transaction.run(t);
        t.exec();

        return response;
    }

    /**
     * Implementation of transaction functionality on redis cluster.
     * Transactions are linked to a single key, and thus are insured to run on
     * the same node.
     *
     * @param key
     *            key to which transaction is related
     * @param transaction
     *            the sequence of redis commands to run
     * @return result of transaction
     */
    public <T> ResponseFacade<T> transaction(final byte[] key, final TransactionRunner<T> transaction) {
        return new JedisClusterCommand<ResponseFacade<T>>(connectionHandler, maxAttempts) {

            @Override
            public ResponseFacade<T> execute(Jedis connection) {
                Transaction t = connection.multi();
                ResponseFacade<T> response = transaction.run(AbstractJedisConnector.wrapJedisTransaction(t));
                t.exec();

                return response;
            }
        }.runBinary(key);
    }

    /**
     * Intentionally providing the functionality in order to get Redis version.
     */
    @Override
    public String info(final String section) {
        // INFO command can be sent to any node
        return new JedisClusterCommand<String>(connectionHandler, maxAttempts) {

            @Override
            public String execute(Jedis connection) {
                return connection.info(section);
            }
        }.runWithAnyNode();
    }

    /**
     * The simple implementation of transaction which is capable of sending each
     * command to redis in order. This transaction is not atomic
     */
    static class TransactionAsSequence implements TransactionFacade {

        private final JedisCluster jedis;
        private final ArrayList<Runnable> operations = new ArrayList<>();
        private Set<byte[]> smembersResult;

        TransactionAsSequence(JedisCluster jedis) {
            this.jedis = jedis;
        }

        @Override
        public void hdel(final byte[] key, final byte[]... fields) {
            operations.add(() -> jedis.hdel(key, fields));

        }

        @Override
        public void hmset(final byte[] key, final Map<byte[], byte[]> hash) {
            operations.add(() -> jedis.hmset(key, hash));
        }

        @Override
        public void del(final byte[]... keys) {
            operations.add(() -> jedis.del(keys));
        }

        @Override
        public ResponseFacade<Set<byte[]>> smembers(final byte[] key) {
            operations.add(() -> {
                smembersResult = jedis.smembers(key);
            });

            return () -> smembersResult;
        }

        /**
         * Executes all submitted operations.
         */
        public void exec() {
            operations.forEach(r -> r.run());
        }
    }
}
