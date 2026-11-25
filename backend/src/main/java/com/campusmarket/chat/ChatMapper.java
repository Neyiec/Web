package com.campusmarket.chat;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.*;

@Mapper
public interface ChatMapper {
    String CONVERSATIONS = """
            SELECT c.id,c.item_id,i.title,c.buyer_id,i.seller_id,
                   IF(c.buyer_id=#{user},s.nickname,b.nickname) AS peer,
                   EXISTS(SELECT 1 FROM chat_messages m WHERE m.conversation_id=c.id AND m.sender_id<>#{user}
                          AND m.id>IF(c.buyer_id=#{user},c.buyer_last_read_message_id,c.seller_last_read_message_id)) AS unread
            FROM conversations c JOIN items i ON i.id=c.item_id
            JOIN users b ON b.id=c.buyer_id JOIN users s ON s.id=i.seller_id
            """;
    String MEMBERS = "(c.buyer_id=#{user} OR i.seller_id=#{user})";

    @Select(CONVERSATIONS + "WHERE " + MEMBERS + " ORDER BY c.last_message_at DESC,c.id DESC")
    List<Conversation> list(long user);

    @Select(CONVERSATIONS + "WHERE c.id=#{id} AND " + MEMBERS)
    Conversation member(@Param("id") long id, @Param("user") long user);

    @Insert("""
            INSERT INTO conversations(item_id,buyer_id) VALUES(#{item},#{buyer})
            ON DUPLICATE KEY UPDATE id=id
            """)
    void open(@Param("item") long item, @Param("buyer") long buyer);

    @Select("SELECT id FROM conversations WHERE item_id=#{item} AND buyer_id=#{buyer}")
    long findId(@Param("item") long item, @Param("buyer") long buyer);

    @Select("""
            SELECT id,sender_id,body,created_at FROM (
                SELECT id,sender_id,body,created_at FROM chat_messages
                WHERE conversation_id=#{id} AND id<#{before}
                ORDER BY id DESC LIMIT 50
            ) history ORDER BY id
            """)
    List<Message> messages(@Param("id") long id, @Param("before") long before);

    @Select("""
            SELECT id,sender_id,body,created_at FROM chat_messages
            WHERE conversation_id=#{id} AND id>#{after} ORDER BY id LIMIT 50
            """)
    List<Message> messagesAfter(@Param("id") long id, @Param("after") long after);

    @Select("SELECT id FROM conversations WHERE id=#{id} FOR UPDATE")
    long lockConversation(long id);

    @Insert("""
            INSERT INTO chat_messages(conversation_id,sender_id,client_message_id,body)
            VALUES(#{id},#{user},#{key},#{body})
            ON DUPLICATE KEY UPDATE id=id
            """)
    void insert(@Param("id") long id, @Param("user") long user, @Param("key") String key,
                @Param("body") String body);

    @Select("""
            SELECT id,sender_id,body,created_at FROM chat_messages
            WHERE conversation_id=#{id} AND sender_id=#{user} AND client_message_id=#{key}
            FOR UPDATE
            """)
    Message findMessage(@Param("id") long id, @Param("user") long user, @Param("key") String key);

    @Update("UPDATE conversations SET last_message_at=GREATEST(COALESCE(last_message_at,#{time}),#{time}) WHERE id=#{id}")
    void touch(@Param("id") long id, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE conversations c JOIN chat_messages m ON m.conversation_id=c.id AND m.id=#{message}
            SET c.buyer_last_read_message_id=IF(c.buyer_id=#{user},GREATEST(c.buyer_last_read_message_id,m.id),c.buyer_last_read_message_id),
                c.seller_last_read_message_id=IF(c.buyer_id=#{user},c.seller_last_read_message_id,GREATEST(c.seller_last_read_message_id,m.id))
            WHERE c.id=#{id}
            """)
    void read(@Param("id") long id, @Param("user") long user, @Param("message") long message);

    record Conversation(long id, long itemId, String title, long buyerId, long sellerId, String peer, boolean unread) {}
    record Message(long id, long senderId, String body, LocalDateTime createdAt) {}
}
