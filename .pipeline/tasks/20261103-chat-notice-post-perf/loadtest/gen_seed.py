"""Generate a deterministic seed for the before/after load test (same data for both DBs)."""
import uuid

NS = uuid.UUID("6f1c2a52-0000-4000-8000-000000000000")
N_USERS = 5000          # broadcast audience
FIRST_USER = 10001
ADMIN_ID = 10000
HOST_ID = 10002          # hosts every party and sends every message
HUBS = range(10003, 10053)        # 50 chat-list requesters, each in every party
VIEWERS = range(10101, 10601)     # 500 post-detail viewers
N_PARTIES, FIRST_PARTY = 30, 1001
MSGS_PER_ROOM, LAST_READ_INDEX = 40, 20

def u(kind, i):
    return str(uuid.uuid5(NS, f"{kind}-{i}"))

out = ["SET FOREIGN_KEY_CHECKS=0;"]
rows = [f"({ADMIN_ID},'{u('user', ADMIN_ID)}','adm','KAKAO','lt-{ADMIN_ID}','ADMIN')"]
rows += [f"({i},'{u('user', i)}','u{i}','KAKAO','lt-{i}','USER')" for i in range(FIRST_USER, FIRST_USER + N_USERS)]
out.append("INSERT INTO user (id,uuid,nickname,auth_provider,external_id,role) VALUES " + ",".join(rows) + ";")
prefs = [f"({i},'NOTICE',0)" for i in range(FIRST_USER, FIRST_USER + N_USERS) if i % 10 == 0]
out.append("INSERT INTO user_notification_preference (user_id,notification_type,enabled) VALUES " + ",".join(prefs) + ";")

parties, rooms, posts, members, msgs = [], [], [], [], []
msg_id = 1
for k in range(N_PARTIES):
    pid = FIRST_PARTY + k
    parties.append(f"({pid},'{u('party', pid)}','2027-01-01','2027-01-05','Seoul',{len(HUBS) + 1},60,'party{k}')")
    rooms.append(f"({pid},'{u('room', pid)}')")
    posts.append(f"({pid},'{u('post', pid)}','post{k}','content','2027-01-01','2027-01-05','Seoul',10,{HOST_ID},{pid})")
    first_msg = msg_id
    for m in range(MSGS_PER_ROOM):
        msgs.append(f"({msg_id},'{u('msg', msg_id)}',{pid},{HOST_ID},'hello {m}')")
        msg_id += 1
    last_read = first_msg + LAST_READ_INDEX - 1
    members.append(f"('HOST','JOINED',NULL,{pid},{HOST_ID})")
    members += [f"('MEMBER','JOINED',{last_read},{pid},{h})" for h in HUBS]

out.append("INSERT INTO party (id,uuid,itin_start,itin_finish,destination,joined,capacity,name) VALUES " + ",".join(parties) + ";")
out.append("INSERT INTO chat_room (room_id,uuid) VALUES " + ",".join(rooms) + ";")
out.append("INSERT INTO post (id,uuid,title,content,itin_start,itin_finish,location,capacity,author_id,party_id) VALUES " + ",".join(posts) + ";")
out.append("INSERT INTO post_view_counts (post_id,views) SELECT id,0 FROM post WHERE id>=1001;")
out.append("INSERT INTO user_party (role,member_status,last_read_message_id,party_id,user_id) VALUES " + ",".join(members) + ";")
out.append("INSERT INTO chat_participant (user_id,room_id) SELECT user_id,party_id FROM user_party WHERE party_id>=1001;")
for s in range(0, len(msgs), 500):
    out.append("INSERT INTO chat_message (id,uuid,room_id,sender_id,body) VALUES " + ",".join(msgs[s:s + 500]) + ";")
out.append("SET FOREIGN_KEY_CHECKS=1;")
print("\n".join(out))
