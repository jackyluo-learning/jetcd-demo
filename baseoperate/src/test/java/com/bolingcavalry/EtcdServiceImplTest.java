package com.bolingcavalry;

import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Response;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import org.junit.jupiter.api.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EtcdServiceImplTest {
    private static final ConfigUtils configUtils = new ConfigUtils(ConfConstants.EVENTMESH_CONF_HOME, ConfConstants.EVENTMESH_CONF_FILE);

    private static final String endpoints = configUtils.getProp(ConfigUtils.ConfKeys.KEYS_ENDPOINT);

    private static final EtcdService etcdService = new EtcdServiceImpl(endpoints);

    @AfterAll
    static void close() {
        etcdService.close();
    }

    private static String key(String name) {
        return "/EtcdServiceImplTest/" + name + "-" + System.currentTimeMillis();
    }

    @Test
    @Order(1)
    @DisplayName("basic put")
    void put() throws Exception {
        Response.Header header = etcdService.put(key("put"), "123");
        assertNotNull(header);
    }

    @Test
    @Order(2)
    @DisplayName("basic get")
    void getSingle() throws Exception {
        String key = key("getSingle");
        String value = String.valueOf(System.currentTimeMillis());

        // 先写入
        etcdService.put(key, value);

        // 再读取
        String queryRlt = etcdService.getSingle(key);

        assertEquals(value, queryRlt);
    }

    @Test
    @Order(3)
    @DisplayName("get with prefix")
    void getWithPrefix() throws Exception {
        String prefix = key("getWithPrefix");

        // 先写入十条
        int num = 10;

        for (int i=0;i<num;i++) {
            // 写入，每个key都不同
            etcdService.put(prefix + i, String.valueOf(i));
        }

        // 带前缀的方式查询，注意要入参key和prefix是同一个值
        GetOption getOption = GetOption.newBuilder().withPrefix(EtcdServiceImpl.bytesOf(prefix)).build();
        GetResponse getResponse = etcdService.getRange(prefix, getOption);

        // 总数应该是十个
        assertEquals(num, getResponse.getCount());
    }

    @Test
    @Order(4)
    @DisplayName("get with limit")
    void getWithLimit() throws Exception {
        String prefix = key("getWithLimit");

        // 先写入十条
        int num = 10;
        int limit = num/2;

        for (int i=0;i<num;i++) {
            // 写入，每个key都不同
            etcdService.put(prefix + i, String.valueOf(i));
        }

        // 带前缀的方式查询，查出来应该是十个，再加上数量限制为五个
        GetOption getOption = GetOption.newBuilder()
                .withPrefix(EtcdServiceImpl.bytesOf(prefix))
                .withLimit(limit)
                .build();

        GetResponse getResponse = etcdService.getRange(prefix, getOption);

        // 总数还是十个
        assertEquals(num, getResponse.getCount());
        // 结果的数量和limit有关，是5个
        assertEquals(limit, getResponse.getKvs().size());
    }

    @Test
    @Order(5)
    @DisplayName("get with revision")
    void getWithRevision() throws Exception {
        String key = key("getWithRevision");

        // 先写入十条
        int num = 10;
        int limit = num/2;

        // 第一次写入时的revision
        long firstRevision = 0L;

        // 第一次写入的value
        String firstValue = null;

        // 最后一次写入的value
        String lastValue = null;

        for (int i=0;i<num;i++) {
            // 用同一个key写十次，每次的value都不同
            String value = String.valueOf(i);
            // 注意，key一直没有变化
            Response.Header header = etcdService.put(key, value);

            // 第一次写入的revision和value都保存下来，后面用revision取出值，和value对比应该相等
            if (0==i) {
                firstRevision = header.getRevision();
                firstValue = value;
            } else if ((num-1)==i) {
                // 将最后一次写入的value记录下来
                lastValue = value;
            }
        }

        // 记录下来的第一次写入的值和最后一次写入的值应该不等
        assertNotEquals(firstValue, lastValue);

        // 如果不带其他条件只用key查找，查出的值应该等于最后一次写入的
        assertEquals(lastValue, etcdService.getSingle(key));

        // 查询条件中指定第一次写入的revision
        GetOption getOption = GetOption.newBuilder()
                .withRevision(firstRevision)
                .build();

        GetResponse getResponse = etcdService.getRange(key, getOption);

        // 总数是一个
        assertEquals(1, getResponse.getCount());

        // 结果的value应该和前面记录的第一次写入的值相等
        assertEquals(firstValue, getResponse.getKvs().get(0).getValue().toString(UTF_8));
    }

    @Test
    @Order(6)
    @DisplayName("read with order")
    void getWithOrder() throws Exception {
        String prefix = key("getWithOrder");

        // 先写入十条，每一条的key都不同，value也不同
        int num = 10;

        // 第一次写的key
        String firstKey = null;
        // 第一次写的value
        String firstValue = null;
        // 最后一次写的key
        String lastKey = null;
        // 最后一次写的value
        String lastValue = null;

        for (int i=0;i<num;i++) {
            String key = prefix + i;
            String value = String.valueOf(i);
            // 写入，每个key都不同
            etcdService.put(key, value);

            // 把第一次写的key、value，最后一次写的key、value保存到对应的变量中
            if(0==i) {
                firstKey = key;
                firstValue = value;
            } else if((num-1)==i) {
                lastKey = key;
                lastValue = value;
            }
        }

        // 第一次查询，结果用key排序，从大到小
        GetOption getOption = GetOption.newBuilder()
                .withPrefix(EtcdServiceImpl.bytesOf(prefix))
                .withSortField(GetOption.SortTarget.KEY)
                .withSortOrder(GetOption.SortOrder.DESCEND)
                .build();

        GetResponse getResponse = etcdService.getRange(prefix, getOption);

        // 总数还是十个
        assertEquals(num, getResponse.getCount());

        // 取查询结果的第一条
        KeyValue firstResult = getResponse.getKvs().get(0);

        // 因为是从大到小，查询结果的第一条应该是最后一次写入的(key是lastKey，value是lastValue)
        assertEquals(lastKey, firstResult.getKey().toString(UTF_8));
        assertEquals(lastValue, firstResult.getValue().toString(UTF_8));


        // 第二次查询，结果用key排序，从小到大
        getOption = GetOption.newBuilder()
                .withPrefix(EtcdServiceImpl.bytesOf(prefix))
                .withSortField(GetOption.SortTarget.KEY)
                .withSortOrder(GetOption.SortOrder.ASCEND)
                .build();

        getResponse = etcdService.getRange(prefix, getOption);

        // 总数还是十个
        assertEquals(num, getResponse.getCount());

        // 取查询结果的第一条
        firstResult = getResponse.getKvs().get(0);

        // 因为是从小到大，查询结果的第一条应该是第一次写入的(key是firstKey，value是firstValue)
        assertEquals(firstKey, firstResult.getKey().toString(UTF_8));
        assertEquals(firstValue, firstResult.getValue().toString(UTF_8));
    }

    @Test
    @Order(7)
    @DisplayName("get only key")
    void getOnlyKey() throws Exception {
        String key = key("getOnlyKey");
        // 写入一条记录
        etcdService.put(key, String.valueOf(System.currentTimeMillis()));

        // 查询条件中指定只返回key
        GetOption getOption = GetOption.newBuilder()
                .withKeysOnly(true)
                .build();

        GetResponse getResponse = etcdService.getRange(key, getOption);

        assertEquals(1, getResponse.getCount());

        KeyValue keyValue = getResponse.getKvs().get(0);

        assertNotNull(keyValue);

        assertEquals(key, keyValue.getKey().toString(UTF_8));

        // value应该是空的
        assertTrue(keyValue.getValue().isEmpty());
    }

    @Test
    @Order(8)
    @DisplayName("get only count")
    void getOnlyCount() throws Exception {
        String key = key("getOnlyCount");
        // 写入一条记录
        etcdService.put(key, String.valueOf(System.currentTimeMillis()));

        // 查询条件中指定只返回key
        GetOption getOption = GetOption.newBuilder()
                .withCountOnly(true)
                .build();

        GetResponse getResponse = etcdService.getRange(key, getOption);

        // 数量应该是1
        assertEquals(1, getResponse.getCount());

        // KeyValue应该是空的
        assertTrue(getResponse.getKvs().isEmpty());
    }

    @Test
    @Order(9)
    @DisplayName("get with end key")
    void getWithEndKey() throws Exception {
        String prefix = key("getWithEndKey");
        String endKey = null;

        int num = 10;

        for (int i=0;i<num;i++) {
            String key = prefix + i;
            // 写入，每个key都不同
            etcdService.put(key, String.valueOf(i));

            // 总共写入十条记录，把第九条的key作为endKey保存
            if ((num-2)==i) {
                endKey = key;
            }
        }

        // 查询条件中指定了endKey是上面写入的第九条记录的key
        // 注意，查询结果中不包含endKey那条记录，也就是说只返回前八条
        GetOption getOption = GetOption.newBuilder()
                .withRange(EtcdServiceImpl.bytesOf(endKey))
                .build();

        GetResponse getResponse = etcdService.getRange(prefix, getOption);

        // 注意，查询结果中不包含endKey那条记录，也就是说只返回前八条
        assertEquals(num-2, getResponse.getCount());
    }

    @Test
    @Order(10)
    @DisplayName("single delete")
    void deleteSingle() throws Exception {
        String key = key("deleteSingle");

        // 写入一条记录
        etcdService.put(key, String.valueOf(System.currentTimeMillis()));

        // 此时应该能查到
        assertNotNull(etcdService.getSingle(key));

        // 删除
        etcdService.deleteSingle(key);

        // 此时应该查不到了
        assertNull(etcdService.getSingle(key));
    }

    @Test
    @Order(11)
    @DisplayName("delete with prefix")
    void deleteWithPrefix() throws Exception {
        String prefix = key("deleteWithPrefix");

        int num = 10;

        // 写入，每个key都不同，但是有相同的前缀
        for (int i=0;i<num;i++) {
            etcdService.put(prefix + i, String.valueOf(i));
        }

        GetOption getOption = GetOption.newBuilder()
                .withPrefix(EtcdServiceImpl.bytesOf(prefix))
                .build();

        // 此时总数应该是十
        assertEquals(num, etcdService.getRange(prefix, getOption).getCount());

        // 删除条件是指定前缀
        DeleteOption deleteOption = DeleteOption.newBuilder()
                .withPrefix(EtcdServiceImpl.bytesOf(prefix))
                .build();

        // 删除
        etcdService.deleteRange(prefix, deleteOption);

        // 删除后再查，总数应该是0
        assertEquals(0, etcdService.getRange(prefix, getOption).getCount());
    }


}
