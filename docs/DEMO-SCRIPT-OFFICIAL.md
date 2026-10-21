# Spring AI Unboxed — kịch bản demo chính thức

## Luồng buổi sharing (~45 phút + hỏi đáp)

**Mạch chính:** một chatbot Kefir AI lớn dần qua từng phần. Mỗi phần giải quyết đúng chỗ còn thiếu của phần trước, nên cuối mỗi phần có một câu **Chuyển phần** để nối sang phần sau.

| Phần | Thời gian | Câu hỏi của phần | Mảnh Spring AI |
|---|---|---|---|
| Mở đầu + 1. Introduction | ~5' | Vì sao cần một framework cho AI? | |
| 1. Spring AI Concepts | ~8' | Các khối cơ bản là gì? | (từ vựng cho cả buổi) |
| 2. Chat streaming | ~4' | Gọi model thế nào? | ChatModel, ChatClient, `stream()` |
| 3. Memory | ~3' | Làm sao bot nhớ hội thoại? | ChatMemory, advisor |
| 4. Upload PDF | ~3' | Đưa tài liệu của shop vào thế nào? | DocumentReader, Splitter, EmbeddingModel, VectorStore |
| 5. RAG | ~4' | Làm sao bot trả lời bằng tài liệu đó? | QuestionAnswerAdvisor |
| 6. Semantic cache | ~3' | Câu hỏi lặp lại có cần gọi model không? | advisor tự viết + VectorStore |
| 7. Tool calling | ~5' | Làm sao bot làm việc thật (đặt đơn)? | `@Tool`, ToolCallingAdvisor |
| 8. Structured output | ~3' | Làm sao code dùng được kết quả? | `responseEntity()` |
| 9. Testing | ~2' | Làm sao biết bot trả lời đúng? | Evaluator |
| 10. MCP | ~3' | AI khác dùng lại tool của app được không? | MCP server |
| Kết thúc | ~2' | Mang về được gì? | |

Phần "Trên slide" và "Trên bot" của mục 2–10 giữ như đã duyệt. Mục 1 chỉ ghi ý chính để nói theo, không phải lời đọc.

---

## Chuẩn bị

### Khởi động app — macOS

Chạy từ thư mục gốc repo.

**1. Bật Rancher Desktop**, chờ đến khi sẵn sàng.

**2. Build jar.** Một lệnh build cả FE và BE: Maven chạy `npm run build` rồi đóng frontend vào jar. Lệnh không in gì khi thành công.

```bash
MAVEN_OPTS="-Djavax.net.ssl.trustStore=$HOME/.m2/truststore/cacerts -Djavax.net.ssl.trustStorePassword=changeit" ./mvnw -B -q -DskipTests package
```

**3. Start.** Script tự bật container Postgres rồi chạy jar; FE nằm sẵn trong jar.

```bash
export DOCKER_HOST=unix:///Users/khanh.le/.rd/docker.sock JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStoreType=KeychainStore -Djavax.net.ssl.trustStore=NONE"
```

```bash
scripts/run.sh
```

Tập luồng không tốn tiền (model giả) thì chạy lệnh này thay cho `scripts/run.sh`:

```bash
scripts/run.sh mock
```

**4. Mở** http://localhost:8080

### Sẵn sàng demo

**1. Reset data.** Login as Admin → **Control panel** → **Reset everything**.

**2. Mở 2 tab riêng.** Tab 1: **Login as User**. Tab 2: **Login as Admin**. Không dùng nút User/Admin ở sidebar vì nó dựng lại màn hình, mất chat đang hiện.

### Dừng app — macOS

App đang chạy ở terminal thì bấm `Ctrl+C`. Nếu không, tắt tiến trình đang giữ cổng 8080:

```bash
kill $(lsof -t -iTCP:8080 -sTCP:LISTEN)
```

Vẫn chưa tắt sau vài giây thì ép tắt:

```bash
kill -9 $(lsof -t -iTCP:8080 -sTCP:LISTEN)
```

Dừng container Postgres (dữ liệu vẫn giữ):

```bash
docker compose stop
```

### Khởi động app — Windows

> ⚠️ **Giả định, chưa chạy lần nào** (các `.ps1` chưa từng chạy). Verify trên máy demo 2026-10-17; sai chỗ nào thì sửa ngay tại đây.

**Việc phải làm trên Mac trước (hôm nay):**
- **Code đã push lên GitHub** (2026-10-16, `main` = `88e5780`). Slide (`demo-assets/architecture.html`) và kịch bản này (`docs/DEMO-SCRIPT-OFFICIAL.md`) nằm trong git, nên pull là có; không cần chép tay.
- **Dự phòng build:** build jar mới trên Mac (lệnh ở bước 2 macOS) và chép `target/spring-AI-app-*.jar` theo, phòng khi Maven trên Windows lỗi TLS.

**0. Cần có trên máy Windows:**
- JDK 25: `java -version` phải báo 25.
- Rancher Desktop (dockerd/moby) đang chạy: `docker info` không báo lỗi.
- Git, và key API Anthropic + OpenAI.
- (Nếu demo MCP) Claude Code đăng nhập **tài khoản cá nhân**.

**1. Lấy code.** Clone vào đường dẫn ngắn (Windows giới hạn độ dài đường dẫn):

```powershell
git clone https://github.com/KhanhLDQ/spring-AI-app.git C:\work\spring-AI-app
```

```powershell
cd C:\work\spring-AI-app
```

Đã clone từ trước thì chỉ cần:

```powershell
git pull
```


**2. Cho phép chạy script** (chỉ trong cửa sổ PowerShell này, không đổi cài đặt máy):

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

**3. Setup (lần đầu, chạy lại cũng không sao).** Script kiểm JDK và Docker, tạo `.env` (mật khẩu DB, JWT key ngẫu nhiên), bật Postgres, rồi build jar. Lần đầu Maven tải thư viện và Node nên mất vài phút.

```powershell
.\scripts\setup.ps1
```

Thêm key API vào `.env`:

```powershell
notepad .env
```

Điền 2 dòng `ANTHROPIC_API_KEY=…` và `OPENAI_API_KEY=…`, rồi lưu.

**Nếu build lỗi TLS** (`PKIX path building failed` / `unable to find valid certification path`): dùng jar build trên Mac. Tạo thư mục `target` nếu chưa có, chép `spring-AI-app-*.jar` vào đó, rồi sang bước 4 (`run.ps1` chỉ cần jar trong `target\`).

**4. Start.** Script đọc `.env`, bật Postgres, chạy jar. Mặc định profile `demo` (model thật):

```powershell
.\scripts\run.ps1
```

Tập không tốn tiền (model giả):

```powershell
.\scripts\run.ps1 mock
```

**5. Mở** http://localhost:8080. Lần chạy đầu với DB trống, app tự nạp 7 tài liệu mẫu (profile `demo` sẽ gọi OpenAI để nhúng, rất rẻ). Nếu từng chạy `mock` rồi đổi sang `demo` (hoặc ngược lại): **Reset everything** ở Control panel.

**6. Mở slide:** double-click `demo-assets\architecture.html` (mở thẳng bằng trình duyệt, không cần server).

Sau đó làm như mục **Sẵn sàng demo** ở trên.

### Dừng app — Windows

App đang chạy ở cửa sổ PowerShell thì bấm `Ctrl+C`. Nếu không, tắt tiến trình đang giữ cổng 8080:

```powershell
Stop-Process -Id (Get-NetTCPConnection -LocalPort 8080 -State Listen).OwningProcess
```

Dừng container Postgres (dữ liệu vẫn giữ):

```powershell
docker compose stop
```

---

## 1. Mở đầu, Introduction và Spring AI Concepts (~13 phút)

### Mở đầu (~1 phút, trước khi chiếu slide)
- Mục tiêu buổi: hiểu các khối chính của Spring AI, thấy chúng chạy thật trên một app, biết khi nào dùng khối nào.
- Cách đi: slide giải thích flow → chạy trên app → xem **Inspector** (cửa sổ nhìn vào bên trong mỗi lượt chat).
- Câu hỏi: để cuối mỗi phần hoặc cuối buổi.

### Trên slide: `demo-assets/architecture.html` → tab **Introduction**

**01 · Thành phần của ứng dụng AI**
- Gọi model chỉ là một phần nhỏ. Một chatbot thật cần thêm: prompt, lịch sử chat, tài liệu, tool, cache, metrics, testing.
- Tự làm thì mỗi phần một thư viện, mỗi nhà cung cấp một SDK, và code nối giữa chúng do mình tự viết.
- Câu dẫn sang slide sau: có cách nào chuẩn hoá cái khung ở giữa không?

**02 · Spring AI**
- Framework chính thức của Spring cho ứng dụng AI.
- So với Spring Data (hàng mờ phía trên): Repository là interface chung, đổi database bằng cấu hình. Spring AI làm y như vậy với `ChatModel` và `VectorStore`.
- Dùng đúng cách quen thuộc của Spring Boot: thêm starter, khai báo trong `application.yaml`, inject bean.

**03 · Ưu điểm của Spring AI**
- Bên trái là tự tích hợp: 3 SDK, 3 kiểu viết. Bên phải là một lớp Spring AI ở giữa.
- Lấy ví dụ từ chính app: chat dùng Anthropic, embedding dùng OpenAI, vector lưu ở pgvector. Hai nhà cung cấp chạy cùng lúc qua cùng một bộ API.
- Memory, RAG, cache gắn vào bằng **advisor**. Tool, structured output, observability có sẵn. Các phần demo sau sẽ đi lần lượt từng mảnh này.

**04 · So sánh giải pháp**
- Không có giải pháp "tốt nhất", chỉ có giải pháp hợp với stack.
- SDK trực tiếp: linh hoạt, nhưng tự làm hết. LangChain: mạnh, nhưng là Python. LangChain4j: Java, nhiều tính năng, nhưng là framework riêng.
- Spring AI: cùng Java, cùng chuẩn Spring Boot mà dự án đang dùng, nên ít code tích hợp nhất.
- ⚠️ Nếu bị hỏi "Spring AI hay LangChain4j hơn?": cả hai đều dùng được cho Java. Chọn theo hệ sinh thái của dự án, không chê bên nào.

**05 · Kiến trúc Kefir AI**
- Giới thiệu app demo: chatbot cho một shop kefir.
- Đi lướt từ trái sang phải: React (Chat, My orders, Admin, Knowledge, Control panel, Inspector) → Spring Boot (controller, `ChatService`, `ChatClient` với chuỗi advisor, tool) → PostgreSQL và pgvector → Anthropic, OpenAI.
- Không cần giải thích từng khối: mỗi tab sau sẽ làm sáng đúng phần của nó trên chính sơ đồ này.

**06 · Nội dung demo**
- 9 phần, mỗi phần thêm một tính năng trên cùng chatbot.
- Mạch: nói chuyện → nhớ → có tài liệu → trả lời từ tài liệu → nhanh và rẻ hơn → làm việc thật → trả dữ liệu cho code → kiểm chất lượng → mở cho AI khác.
- Testing chỉ trình bày slide; MCP tuỳ máy demo.

### Trên slide: tab **Spring AI Concepts** (14 slide, ~30 giây mỗi slide)

Mục tiêu tab này là từ vựng, chưa có code. Mỗi slide nói một ý, rồi hẹn "sẽ thấy chạy thật ở phần N".

| Slide | Ý chính | Gặp lại ở |
|---|---|---|
| ChatModel | Interface chung để gọi model; mỗi nhà cung cấp là một implementation, đổi bằng cấu hình | 2 |
| ChatClient | API fluent gom prompt, advisor, tool và kiểu kết quả; chọn `call()` hay `stream()` | 2 |
| Advisor | Lớp bao quanh mỗi lần gọi model, sửa request và response; thứ tự advisor quyết định hành vi. Đây là cách gắn tính năng mà không sửa code gọi model | 3, 5, 6 |
| ChatMemory | Model không nhớ gì giữa các lần gọi; app lưu lịch sử và gửi lại. Thiếu memory thì "đổi thành 4 chai" vô nghĩa | 3, 7 |
| EmbeddingModel | Biến câu thành vector; câu cùng nghĩa cho vector gần nhau, nên so được theo nghĩa | 4 |
| VectorStore | Lưu vector, tìm top-k vector gần câu hỏi nhất; app dùng pgvector | 4, 5 |
| Cosine Similarity | Điểm 0 → 1 theo góc giữa hai vector. Số đo thật: 0,27 không liên quan · 0,65 cùng chủ đề khác ý · 0,81 diễn đạt lại. Từ đây ra ngưỡng RAG 0,50 và cache 0,75 | 5, 6 |
| RAG | Retrieve → Augment → Generate: tìm đoạn liên quan, chèn vào prompt, model trả lời kèm nguồn | 5 |
| Upload Documents (ETL) | Read → Split → Embed → Store, làm một lần lúc tải tài liệu | 4 |
| Tool Calling | Model chỉ **yêu cầu** gọi hàm, app mới là bên chạy hàm rồi gửi kết quả lại | 7 |
| Structured Output | Đưa khuôn dữ liệu, nhận JSON đúng khuôn, chuyển thành object Java | 8 |
| Observability | Đo thời gian, token, chi phí của từng bước; Inspector của app dựng trên dữ liệu này | xuyên suốt |
| MCP | Giao thức chuẩn để AI client bên ngoài gọi tool của app: viết tool một lần, nhiều client dùng | 10 |
| Evaluator | Dùng một model khác chấm câu trả lời (LLM-as-a-judge), vì câu trả lời AI không cố định từng chữ | 9 |

**Chuyển phần:** "Đủ từ vựng rồi. Giờ bắt đầu từ cái đơn giản nhất: gọi model, chưa bật thêm gì."

---

## 2. Chat streaming (~4 phút)

### Trên slide: `demo-assets/architecture.html` → tab **Chat streaming**

**01 · Cấu hình model**
- **Nói:** "Gọi model trong Spring AI bắt đầu từ cấu hình, chưa cần code. Thêm starter Anthropic và khai báo model trong `application.yaml`."
- **Click** `</>`: chỉ vào `model: claude-sonnet-5-5`. Đổi model chỉ cần sửa một dòng.

**02 · ChatModel**
- **Nói:** "Auto-configuration đọc cấu hình rồi tạo `AnthropicChatModel`. Đây là interface `ChatModel`: đổi sang OpenAI thì đổi starter và cấu hình, code gọi model giữ nguyên."

**03 · ChatClient**
- **Nói:** "Ứng dụng không gọi `ChatModel` trực tiếp mà qua `ChatClient`, một API fluent dựng từ `ChatModel`."
- **Click** `</>`: `ChatClient.builder(chatModel).build()`.
- **Nói thêm:** "Các advisor trong khung `ChatClient` đang mờ; phần sau sẽ lần lượt thêm vào."

**04 · call() / stream()**
- **Nói:** "Có 2 cách nhận câu trả lời. `call()` chờ model trả lời xong rồi trả một lần. `stream()` trả về `Flux`: mỗi phần câu trả lời đi qua SSE tới trình duyệt ngay khi model sinh ra. Chat dùng `stream()` để người dùng thấy chữ hiện dần."
- **Click** `</>`: hai dòng chỉ khác `.call().content()` và `.stream().chatResponse()`.

### Trên bot: tab **User**

1. Bấm **New chat**. Kiểm hàng công tắc: **Memory, RAG, Cache, Tools đều tắt** (mặc định; đang bật thì bấm **All off**).
2. Gõ: **"Kefir là gì? Kể 3 lợi ích cho sức khoẻ."**
3. **Chỉ cho khán giả:**
   - Chữ hiện dần từng phần: đó là `stream()`.
   - Dưới câu trả lời: thời gian, token, chi phí, `CACHE OFF`, `OFF: memory, rag, cache, tools`.
   - **Inspector → Behind the scenes** (đọc từ trên xuống, xem mục giải thích bên dưới).
4. **Kết quả mong đợi:** câu trả lời vài đoạn về kefir và 3 lợi ích. Lần chạy thử (Sonnet 5.5): 5,3 s, 235 in / 393 out, $0,0044, 82 chunk, chunk đầu ở 2,7 s.
5. **Nói:** "Chỉ với cấu hình và `ChatClient`, model đã trả lời được kiến thức chung. Hỏi về sản phẩm của shop thì model không biết: các phần sau sẽ đưa dữ liệu của shop vào."

### Behind the scenes: đọc thế nào

Mọi dòng lấy từ dữ liệu thật của lượt chat, không suy ra từ câu trả lời:
- *Request* và *Response*: observation của `ChatModel` mà Spring AI phát ra cho mỗi lần gọi model (`TraceObservationHandler`).
- *First chunk* và *Chunk N of N*: app đếm từng `ChatResponse` đi qua `Flux` (`StreamTrace`).

| Dòng | Nói gì |
|---|---|
| **Request** · App → Model | Prompt đúng như Spring AI gửi, sau khi mọi advisor đã chạy: system + user, không gửi tool, giới hạn 1500 token. Mở **Prompt as sent** để xem nguyên văn system prompt. |
| **First chunk** · Model → App | Mẩu chữ đầu tiên về tới app. Trước đó model đang đọc prompt và bắt đầu sinh, nên chưa có gì (time to first token, ~2–3 s). "81 carried text": 81 mẩu có chữ, mỗi mẩu thành một event SSE `token` gửi ngay tới trình duyệt. |
| **Chunk 82 of 82** · Model → App | Mẩu cuối **không có chữ**, chỉ mang `end_turn`: model báo đã nói xong. Đây là lúc stream biết model dừng. |
| **Response** · Model → App | Observation đóng: lý do dừng, token vào/ra, có tool call hay không. Không có tool call nên vòng tool kết thúc. |
| **Turn complete** · App | App gửi các step và event `done` về trình duyệt. |

### Nếu có người hỏi

**Model gửi gì về cho app?**
Anthropic stream bằng SSE, gồm nhiều sự kiện. Các sự kiện `content_block_delta` mang mẩu chữ (`text_delta`), sự kiện `message_delta` mang `stop_reason` và số token. Spring AI đổi mỗi mẩu chữ và mẩu báo dừng thành một `ChatResponse` trong `Flux`; các sự kiện mở/đóng (`message_start`, `message_stop`) không thành chunk riêng. Chữ đọc ở `getResult().getOutput().getText()`, lý do dừng đọc ở `getResult().getMetadata().getFinishReason()`. Đo thật: 81 chunk có chữ, chunk thứ 82 chỉ có `end_turn`.

**1 chunk có phải 1 token không?**
Không. 82 chunk chứa 393 output token; mỗi chunk thường gồm vài token.

**`stop_reason` có những giá trị nào? App làm gì với mỗi giá trị?**
- `end_turn`: model trả lời xong → dừng, trả response.
- `tool_use`: model muốn gọi tool, response kèm tên tool và input JSON → `ToolCallingAdvisor` chạy tool, gửi `tool_result`, gọi model vòng 2 (xem phần Tool calling).
- `max_tokens`: hết giới hạn (1500), câu trả lời bị cắt.

**Vì sao chunk đầu đến muộn (~2–3 s) mà cả lượt chỉ ~5 s?**
Model phải đọc hết prompt rồi mới sinh mẩu đầu (time to first token). Sau đó các mẩu đến liên tục. Với `call()`, người dùng phải chờ hết ~5 s mới thấy chữ; với `stream()` thì thấy chữ từ ~2,7 s.

**"Response" và "Chunk 82" cùng thời điểm, cái nào trước?**
Chunk cuối đến trước, rồi stream đóng và Spring AI đóng observation; vì vậy "Response" xếp ngay sau.

**Số liệu có phải minh hoạ không?**
Không. Request lấy từ `ChatModelObservationContext.getRequest()`, response từ `getResponse()`, chunk được đếm khi đi qua `Flux` trong `ChatService`. Mock cũng mô phỏng đúng kiểu Anthropic: chunk cuối không có chữ, chỉ mang lý do dừng.

### Nếu có sự cố
- **Không có chữ nào sau ~60 giây:** app báo timeout. Bấm **New chat** rồi gửi lại.

**Chuyển phần:** "Bot trả lời được, nhưng mỗi lần gọi model là độc lập: nói tên xong, hỏi lại là quên."

---

## 3. Memory (~3 phút)

### Trên slide: `demo-assets/architecture.html` → tab **Memory**

**01 · ChatMemory**
- **Nói:** "Model không tự nhớ gì giữa các lần gọi; mỗi request là độc lập. Muốn bot nhớ, ứng dụng phải lưu lịch sử và gửi lại. Spring AI làm việc này qua `ChatMemory`."
- **Click** `</>`: starter JDBC, `initialize-schema: never` (bảng do Flyway tạo), `window-size: 20`, bean `MessageWindowChatMemory`.

**02 · MessageChatMemoryAdvisor**
- **Nói:** "Gắn `MessageChatMemoryAdvisor` vào `ChatClient`. Từ đó mọi lần gọi model đều đi qua advisor này, không phải sửa code gọi model."

**03 · Conversation ID**
- **Nói:** "Mỗi request truyền `conversationId` để advisor biết lấy lịch sử nào. Key gồm cả username, nên người này không đọc được hội thoại của người khác."

**04 · Nạp lịch sử vào prompt**
- **Nói:** "Trước khi gọi model, advisor đọc tin cũ trong `spring_ai_chat_memory` và chèn vào prompt, trước câu hỏi mới."

**05 · Lưu lượt mới**
- **Nói:** "Stream xong, câu hỏi và câu trả lời được lưu thành 2 tin mới."

### Trên bot: tab **User**, bấm **New chat**

| Lượt | Công tắc | Gõ | Mong đợi | Chỉ vào Inspector |
|---|---|---|---|---|
| 1 | Bật **Memory** (chỉ Memory) | **"Tên mình là Lan"** | Bot chào Lan | Card Memory: `0 earlier message(s) (new conversation)`. Behind the scenes → Request: `system · user` |
| 2 | **Tắt** Memory | **"Mình tên gì?"** | Bot nói chưa biết tên | Dòng "Switched off" có Memory. Request: `system · user` (không có lịch sử) |
| 3 | **Bật** lại Memory | **"Mình tên gì?"** | **"Lan"** | Card Memory: `2 earlier message(s)`. Request: `system · user ×2 · assistant`. Mở **Prompt as sent**: thấy "Tên mình là Lan" và câu trả lời lượt 1 |

- **Nói sau lượt 2:** "Lượt tắt Memory không đọc và cũng không lưu, nên ở lượt 3 lịch sử chỉ có lượt 1."
- **Nói ở lượt 3:** "Model không nhớ gì. Bot biết tên là vì Spring AI gửi lại lịch sử trong prompt: đây là prompt thật vừa gửi đi."

### Kiểm trong database: bảng `spring_ai_chat_memory`

Mở bảng trong DB UI. Lọc theo hội thoại mới nhất:

```sql
SELECT type, replace(left(content, 50), E'\n', ' ') AS content, "timestamp"
FROM spring_ai_chat_memory
WHERE conversation_id = (SELECT conversation_id FROM spring_ai_chat_memory ORDER BY "timestamp" DESC LIMIT 1)
ORDER BY "timestamp", sequence_id;
```

**Mong đợi sau lượt 3: đúng 4 dòng** `USER` "Tên mình là Lan" → `ASSISTANT` chào Lan → `USER` "Mình tên gì?" → `ASSISTANT` "Bạn tên là Lan". Lượt 2 (Memory tắt) **không có trong bảng**. Đã chạy thật 2026-10-16.

- **Nói:** "Đây là bảng Spring AI dùng để nhớ. Lượt tắt Memory không được lưu, nên chỉ có 4 dòng của lượt 1 và lượt 3."

### Nếu có người hỏi

**Lưu được bao nhiêu tin?**
Tối đa `window-size` tin cho mỗi hội thoại; app đặt 20, trùng mặc định của Spring AI (`DEFAULT_MAX_MESSAGES = 20`). Một lượt = 2 tin (hỏi + đáp), nên 20 tin ≈ 10 lượt gần nhất.

**Quá 20 tin thì sao?**
Mỗi lần lưu, `MessageWindowChatMemory` lấy toàn bộ tin, nối thêm tin mới, cắt còn 20 tin gần nhất, rồi `saveAll()`. Repository JDBC xoá hết dòng của hội thoại rồi ghi lại trong cùng một transaction, nên tin cũ **bị xoá khỏi bảng**, không chỉ bị ẩn khỏi prompt.

**Bảng memory có tự xoá không? User spam thì bảng có phình không?**
- **Trong một hội thoại: không phình.** Mỗi `conversationId` giữ tối đa 20 dòng; tin cũ hơn bị xoá khỏi bảng (như câu trên). Spam trong cùng một chat chỉ làm cửa sổ trượt, số dòng vẫn ≤ 20.
- **Giữa các hội thoại: có phình.** Spring AI **không có TTL hay job dọn** cho chat memory. Mỗi lần New chat (hoặc F5) là một `conversationId` mới với tối đa 20 dòng, nên bảng tăng theo **số hội thoại**. App chỉ xoá khi Admin bấm **Reset everything** (`TRUNCATE`, `ChatHistoryService.clearAll()`).
- **Chi phí thật của spam là ghi DB:** mỗi lượt `saveAll()` xoá rồi ghi lại cả cửa sổ (~20 dòng), không phải chỉ thêm 2 dòng.
- **Làm production thì thêm:**
  - Job định kỳ xoá hội thoại không hoạt động quá N ngày. Bảng có cột `timestamp` và index `(conversation_id, "timestamp")`:
    ```sql
    DELETE FROM spring_ai_chat_memory
    WHERE conversation_id IN (SELECT conversation_id FROM spring_ai_chat_memory
                              GROUP BY conversation_id HAVING max("timestamp") < now() - interval '30 days');
    ```
  - Gọi `chatMemory.clear(conversationId)` khi người dùng xoá hoặc đóng chat.
  - Rate limit theo user và giới hạn số hội thoại mỗi user.
- **Trong app hiện có:** câu hỏi tối đa 2000 ký tự (giới hạn cỡ mỗi dòng) và trần chi phí `kefir.budget.limit-usd: 5` (hết thì từ chối lượt chat). Chưa có job dọn và rate limit.

**Đổi được số 20 không?**
Được, nhiều hơn hay ít hơn đều được, miễn > 0 (0 hoặc âm: lỗi lúc khởi động `maxMessages must be greater than 0`). Đổi xong phải khởi động lại app.
- **Ít hơn** (vd 4): nhớ ~2 lượt, prompt ngắn và rẻ, nhưng quên nhanh.
- **Nhiều hơn** (vd 50): nhớ ~25 lượt, nhưng cả cửa sổ được gửi lại mỗi lượt nên tốn token và chậm hơn. Vẫn phải vừa context window của model.

**`initialize-schema: never` là gì?**
Không để Spring AI tự tạo bảng. Bảng do Flyway tạo (`V4`), vì `conversation_id` dạng `username:uuid` dài tới 86 ký tự, còn bảng mặc định chỉ có `VARCHAR(36)`.

**Có giới hạn input token không?**
`max-tokens` chỉ giới hạn output. Input chỉ bị giới hạn bởi context window của model; app giữ input nhỏ bằng cửa sổ 20 tin, câu hỏi tối đa 2000 ký tự, và RAG tối đa 3 chunk.

**Memory có lưu lời gọi tool không?**
Không, chỉ lưu text hỏi/đáp (xem lại ở phần Tool calling).

**Bài học khi làm**
Bọc `MessageChatMemoryAdvisor` theo kiểu `BaseAdvisor` làm mất bước lưu khi stream (advisor gốc tự lưu khi stream kết thúc). Vì vậy app gọi thẳng advisor gốc và chỉ ghi thêm step cho Inspector.

### Nếu có sự cố
- **Lượt 3 bot không nhớ tên:** kiểm Memory đã bật ở cả lượt 1 và lượt 3, và cả 3 lượt nằm trong **cùng một chat** (không bấm New chat ở giữa).

**Chuyển phần:** "Bot đã nhớ hội thoại, nhưng chưa biết gì về shop: hỏi kefir mật ong để được bao lâu thì không trả lời được. Bước đầu là nạp tài liệu của shop vào."

---

## 4. Upload PDF (~3 phút)

### Trên slide: `demo-assets/architecture.html` → tab **Upload PDF**

**01 · Upload**
- **Nói:** "Muốn bot trả lời bằng tài liệu của shop, trước hết phải nạp tài liệu vào. Admin tải file lên; KnowledgeService tra `knowledge_docs` để từ chối file trùng. Bước này chỉ đọc, chưa ghi."

**02 · DocumentReader (Extract)**
- **Nói:** "`PagePdfDocumentReader` đọc PDF, mỗi trang thành một `Document`, kèm tên file và số trang trong metadata. Nhờ metadata này mà sau này bot ghi được nguồn."
- **Click** `</>`: vòng lặp tạo `Document` với `source` và `page`.

**03 · TokenTextSplitter (Transform)**
- **Nói:** "Một trang có thể dài. `TokenTextSplitter` cắt thành chunk khoảng 400 token, để lúc tra chỉ lấy đúng đoạn liên quan thay vì cả trang."

**04 · EmbeddingModel và VectorStore (Load)**
- **Nói:** "`VectorStore.add()` gọi EmbeddingModel, ở đây là OpenAI `text-embedding-3-small`, đổi từng chunk thành vector 1536 chiều rồi lưu vào pgvector. Anthropic không có API embedding nên phần này dùng OpenAI."
- **Click** `</>`: `vectorStore.add(chunks)`, builder `PgVectorStore` (1536 chiều, cosine), yaml embedding.

**05 · Lưu tài liệu**
- **Nói:** "KnowledgeService ghi `knowledge_docs` trong cùng transaction với các chunk. Nếu nhúng lỗi giữa chừng thì không có gì được ghi."

### Trên bot: tab **Admin** → màn **Knowledge**

1. Danh sách có 7 tài liệu mẫu (Markdown).
2. Bấm upload, chọn `demo-assets/uu-dai-thang-10.pdf`.
3. **Chỉ vào panel Pipeline:** thời gian Read / Split / Embed / Store và số chunk, tương ứng Extract, Transform, Load trên slide. Thường Embed lâu nhất vì phải gọi API của OpenAI.
4. Tài liệu mới hiện trong danh sách, kèm số chunk.

### Kiểm trong database

**Cách 1: mở bảng, lọc theo điều kiện**
- **`knowledge_docs`**, lọc `filename = 'uu-dai-thang-10.pdf'` → 1 dòng, `chunk_count` bằng số chunk trên panel Pipeline.
- **`vector_store`**, lọc `metadata->>'source' = 'uu-dai-thang-10.pdf'` → số dòng bằng `chunk_count`; mỗi dòng có `content` (đoạn text), `metadata` (`source`, `page`, `chunk_index`…), `embedding` (vector 1536 số).

**Cách 2: chạy SQL** (gọn hơn khi trình chiếu: cột `embedding` đầy đủ dài 1536 số, câu này chỉ lấy số chiều và 3 số đầu)

```sql
SELECT filename, chunk_count, uploaded_at FROM knowledge_docs WHERE filename = 'uu-dai-thang-10.pdf';
```

```sql
SELECT metadata->>'page' AS page, metadata->>'chunk_index' AS chunk,
       replace(left(content, 50), E'\n', ' ') AS content,
       vector_dims(embedding) AS dims, (embedding::real[])[1:3] AS first3
FROM vector_store WHERE metadata->>'source' = 'uu-dai-thang-10.pdf';
```

| Cột | Ý nghĩa |
|---|---|
| `page` | Chunk từ trang mấy của PDF |
| `chunk` | Thứ tự chunk khi một trang bị cắt nhiều đoạn |
| `content` | 50 ký tự đầu của đoạn text |
| `dims` | Số chiều của vector: **1536** |
| `first3` | 3 con số đầu của vector |

- **Nói:** "Mỗi chunk là một dòng: đoạn text, metadata để biết nguồn và trang, và một vector 1536 con số. Phần RAG sẽ so câu hỏi với chính các vector này."

### Nếu có người hỏi

**Lưu thành chunk + vector khác gì lưu nguyên file?**
- **Tìm:** nguyên file chỉ tìm được chữ khớp, hỏi diễn đạt khác là trượt. Vector so theo nghĩa: "khuyến mãi tháng này" vẫn ra "Ưu đãi tháng 10".
- **Prompt:** nguyên file phải nhét cả file mỗi lần hỏi, tốn token, chậm, có thể vượt context window. Chunk chỉ chèn top-3 đoạn liên quan, khoảng 1.200 token.
- **Nguồn:** chunk biết đúng file và trang nhờ metadata.
- **Chi phí:** nhúng một lần lúc upload, rất rẻ; mỗi câu hỏi chỉ thêm 1 lần nhúng câu hỏi.
- **Một câu:** "Lưu nguyên file thì bot phải đọc lại cả file mỗi lần hỏi; lưu chunk + vector thì bot chỉ đọc vài đoạn liên quan và biết đoạn đó ở trang nào."
- **Lưu ý:** app không giữ file gốc, chỉ giữ chunk. Vì vậy đổi chunk size thì không cắt lại được PDF đã tải, và PDF bị xoá.

**Vì sao upload lại gọi OpenAI?**
`VectorStore.add()` tự gọi EmbeddingModel để có vector rồi mới lưu. Anthropic không có API embedding nên app dùng OpenAI. Câu hỏi ở phần RAG cũng phải nhúng bằng đúng model này: vector chỉ so được với nhau khi cùng một model sinh ra.

**Metadata có những gì?**
`source` và `page` do app gắn khi đọc PDF. `chunk_index`, `total_chunks`, `parent_document_id` do `TokenTextSplitter` thêm khi cắt chunk.

**Upload lại cùng file thì sao?**
Bị từ chối (409) vì trùng tên hoặc trùng SHA-256 trong `knowledge_docs`.

**PDF scan (ảnh) thì sao?**
Không đọc được chữ nên bị từ chối với thông báo cần OCR trước.

### Nếu có sự cố
- **Lỗi 409 "trùng file":** PDF đã được tải từ lần tập trước. Control panel → **Reset everything**, rồi tải lại.

**Chuyển phần:** "Tài liệu đã thành vector trong pgvector, nhưng chat chưa dùng tới. RAG nối hai phần này."

---

## 5. RAG (~4 phút)

### Trên slide: `demo-assets/architecture.html` → tab **RAG**

**01 · QuestionAnswerAdvisor**
- **Nói:** "Tài liệu đã nằm trong vector store. Giờ cho chatbot dùng chúng. Spring AI có sẵn `QuestionAnswerAdvisor`; app chỉ cấu hình top-k 3 và ngưỡng 0,50."
- **Click** `</>`: builder với `topK`, `similarityThreshold`, `promptTemplate`; yaml `top-k`, `similarity-threshold`.

**02 · Nhúng câu hỏi**
- **Nói:** "Câu hỏi được nhúng thành vector bằng đúng model đã dùng lúc upload. Khác model thì vector không so được với nhau."

**03 · similaritySearch**
- **Nói:** "pgvector so vector câu hỏi với mọi chunk bằng cosine, lấy 3 chunk gần nhất có điểm từ 0,50 trở lên. Không chunk nào đạt thì không chèn gì."

**04 · Chèn chunk vào prompt**
- **Nói:** "Các chunk được ghép vào ngay sau câu hỏi, trong thẻ `<knowledge>`, kèm lời dặn: ưu tiên tri thức này, không có thì nói không biết."
- **Click** `</>`: prompt template.

**05 · Trả lời kèm nguồn**
- **Nói:** "Model trả lời dựa trên các chunk. Metadata `source` và `page` từ lúc upload thành chip nguồn dưới câu trả lời."

### Trên bot: tab **User**, bấm **New chat**, **Memory tắt** cả 3 lượt

| Lượt | Công tắc | Gõ | Mong đợi | Chỉ vào Inspector |
|---|---|---|---|---|
| 1 | **Tắt hết** | **"Kefir mật ong để được bao lâu?"** | "Chưa có thông tin chính xác" | Không có card Retrieval. Behind the scenes → **Prompt as sent**: user chỉ có câu hỏi (vài chục ký tự) |
| 2 | **Chỉ RAG bật** | cùng câu | **"Dưới 3 ngày"**, có chip nguồn | Card **Route: retrieve knowledge**; card **Retrieval: 3 chunk(s)** (đoạn text, điểm, nguồn). **Prompt as sent**: user dài hơn hẳn, câu hỏi + `<knowledge>…</knowledge>` |
| 3 | RAG bật | **"Ưu đãi tháng 10 có gì?"** | Trả lời từ PDF; chip ghi `uu-dai-thang-10.pdf · p.N` | Card Retrieval: chunk từ `uu-dai-thang-10.pdf` lên đầu |

- **Nói ở lượt 2:** "Cùng một câu hỏi, cùng một model. Khác nhau duy nhất là Spring AI đã tìm 3 đoạn liên quan và chèn vào prompt: đây là prompt thật vừa gửi đi."
- **Nói ở lượt 3:** "Đây là PDF vừa tải lên ở phần trước. Chip nguồn ghi đúng trang, nhờ metadata gắn lúc đọc PDF."
- Lượt 3 cần PDF đã tải ở phần Upload và chưa Reset sau đó.

### Card "Route" trong Inspector (giải thích khi chiếu)
- **Nói:** "Trước khi tìm, app có thêm một bước tự viết: router so câu hỏi với các câu mẫu. Nếu chắc chắn là yêu cầu đặt đơn thì bỏ qua tìm kiếm cho nhanh và rẻ. Câu này là câu hỏi tri thức, nên vẫn tìm."
- Card có 2 thanh điểm: "Closest order example" và "Closest knowledge example".

### Nếu có người hỏi

**Top-k và ngưỡng nghĩa là gì?**
Top-k 3: chèn tối đa 3 chunk. Ngưỡng 0,50: chunk phải giống câu hỏi ít nhất 0,50 (cosine) mới được chèn. Kéo thanh ngưỡng RAG ở Control panel: thấp quá thì chèn cả chunk không liên quan; cao quá thì không chunk nào đạt, và bot nói không biết.

**Chỉ 2 chunk trên ngưỡng, hoặc nhiều hơn 3 chunk trên ngưỡng thì sao?**
Top-k là **số tối đa**, ngưỡng là **bộ lọc**; hai điều kiện chạy cùng lúc (lọc điểm ≥ 0,50, xếp giảm dần, lấy tối đa 3).
- **Chỉ 2 chunk ≥ 0,50:** chèn đúng 2, không lấy thêm chunk dưới ngưỡng cho đủ 3.
- **Hơn 3 chunk ≥ 0,50:** chèn 3 chunk điểm cao nhất, còn lại bỏ dù vẫn trên ngưỡng.
- **Không chunk nào ≥ 0,50:** không chèn gì; Inspector ghi "Retrieval: no chunk above threshold", bot nói không biết.
- **Ví dụ thật** (đo 2026-10-14): "Kefir mật ong để được bao lâu?" có top-5 điểm 0,68 · 0,65 · 0,60 · 0,59 · 0,59. Cả 5 trên 0,50 nhưng chỉ chèn 0,68, 0,65, 0,60; hai chunk 0,59 bị bỏ vì vượt top-k.
- **Một câu:** "Ngưỡng quyết định chunk nào đủ tốt, top-k quyết định lấy tối đa bao nhiêu."

**Vì sao chunk nối vào câu hỏi của user, không vào system prompt?**
Đó là cách mặc định của `QuestionAnswerAdvisor`: tri thức đi kèm đúng câu hỏi của lượt này. System prompt giữ vai trò và luật chung, không đổi theo từng câu hỏi.

**RAG lấy chunk thế nào khi có nhiều tài liệu?**
So với **mọi** chunk của mọi tài liệu và lấy theo điểm, không chọn theo file. Vì vậy câu ưu đãi lấy được chunk từ PDF dù còn 7 tài liệu khác.

**Router là gì, có phải Spring AI không?**
Không, đây là phần app tự viết (Advanced RAG: routing). Router nhúng câu hỏi, so với câu mẫu "đặt đơn" và "tri thức", chỉ bỏ qua tìm kiếm khi chắc chắn là đặt đơn; câu lẫn cả hai vẫn tìm. Spring AI có các khối cho Advanced RAG (`RetrievalAugmentationAdvisor`, `RewriteQueryTransformer`, `MultiQueryExpander`…), app chưa dùng.

**Advisor khác gì tool `searchKnowledge`?**
Advisor **luôn chạy** trước khi gọi model: chắc chắn, dễ đoán. Tool thì **model tự quyết** có gọi hay không: linh hoạt hơn nhưng có thể bỏ sót. App dùng advisor cho chat; tool `searchKnowledge` dành cho MCP.

**Có cách nào xem riêng bước tìm kiếm không?**
Màn Knowledge (Admin) → **Test retrieval**: chạy `similaritySearch` top-5, không ngưỡng, không gọi model, nên thấy cả chunk điểm thấp bị loại.

### Nếu có sự cố
- **Lượt 2 vẫn nói không biết:** kiểm RAG đã bật, và ngưỡng RAG ở Control panel là 0,50 (Reset trả về mặc định).
- **Lượt 3 không có chunk từ PDF:** PDF chưa tải, hoặc đã bị xoá do Reset hay đổi chunk size. Tải lại ở màn Knowledge.

**Chuyển phần:** "Mỗi câu hỏi RAG phải nhúng, tìm, rồi gọi model: vài giây và tốn tiền. Khách lại hay hỏi cùng một ý theo nhiều cách."

---

## 6. Semantic cache (~3 phút)

### Trên slide: `demo-assets/architecture.html` → tab **Semantic cache**

**01 · SemanticCacheAdvisor**
- **Nói:** "Semantic cache dùng lại câu trả lời cũ khi câu hỏi mới **cùng nghĩa** (không cần cùng chữ) với câu đã hỏi. Spring AI không có sẵn; app tự viết một advisor đứng ngoài cùng chuỗi, dùng VectorStore thứ hai là bảng `semantic_cache`."
- **Click** `</>`: `getOrder()` ngoài cùng, `PgVectorStore` cho `semantic_cache`, yaml ngưỡng 0,75 và TTL 24h.

**02 · Ghi cache**
- **Nói:** "Chỉ lưu câu trả lời dựa trên tài liệu của shop. Không lưu khi lượt có gọi tool (dữ liệu đơn hàng thay đổi), khi không có chunk (không có nguồn), hoặc câu dựa vào hội thoại trước như 'đổi thành 4 chai'."
- **Click** `</>`: 3 điều kiện trong `whyNotReusable()`.

**03 · Tra cache**
- **Nói:** "Câu hỏi mới được nhúng, tìm câu đã lưu gần nhất, cùng role, trong 24 giờ."

**04 · HIT**
- **Nói:** "Điểm từ 0,75 trở lên là coi như cùng câu hỏi: trả lại câu trả lời đã lưu, không gọi model."

### Trên bot: tab **User**, bấm **New chat**, bật **RAG + Cache**, Memory tắt

| Lượt | Gõ | Mong đợi | Chỉ vào Inspector |
|---|---|---|---|
| 1 | **"Kefir mật ong để được bao lâu?"** | "Dưới 3 ngày", ~4–5 s, `CACHE MISS` | Card **Semantic cache: miss**; card **Cache write: stored** |
| 2 | **"Kefir mật ong bảo quản được bao lâu vậy?"** | Cùng câu trả lời, `CACHE HIT`, ~0,5 s, **$0** | Card **Semantic cache: HIT** (điểm ≥ 0,75, câu đã khớp). Behind the scenes: **"Answered from the cache. No request was sent to the model."** Chip nguồn vẫn còn |

- **Nói ở lượt 1:** "Lần đầu chưa có gì trong cache: chạy RAG, gọi model như bình thường, rồi lưu lại."
- **Nói ở lượt 2:** "Khác chữ, cùng nghĩa: cache nhận ra nhờ so vector chứ không so chữ. Không gọi model nên gần như tức thì và không tốn tiền."
- Lượt 1 phải là câu **chưa từng được cache** (ở phần RAG câu này chạy khi Cache tắt nên chưa được ghi). Đã tập với Cache bật thì **Reset** trước.

### Kiểm trong database: bảng `semantic_cache`

**Cách 1: mở bảng.** Sau lượt 1 có 1 dòng: `content` là câu hỏi (cột được nhúng thành vector), `metadata` chứa `answer`, `role`, `createdAt`, `sources`.

**Cách 2: SQL**

```sql
SELECT content AS question, metadata->>'role' AS role,
       replace(left(metadata->>'answer', 50), E'\n', ' ') AS answer,
       to_timestamp((metadata->>'createdAt')::bigint / 1000) AS created_at
FROM semantic_cache ORDER BY created_at DESC;
```

- **Nói:** "Cache lưu **câu hỏi** dưới dạng vector để so nghĩa, còn câu trả lời nằm trong metadata. Lượt 2 không thêm dòng mới vì đã dùng lại dòng này."

### Nếu có người hỏi

**Hỏi cùng chủ đề nhưng khác ý thì sao?**
Ví dụ "Kefir Mè Đen để được bao lâu?": gần chủ đề với câu mật ong, nhưng điểm thấp hơn ngưỡng (đo trước đây: câu cùng chủ đề khác ý chỉ 0,64–0,69) nên **MISS**, model trả lời mới. Riêng câu Mè Đen chưa đo.

**Ngưỡng 0,75 chọn thế nào?**
Đo thật với `text-embedding-3-small`: câu diễn đạt lại đạt 0,80–0,82, câu cùng chủ đề khác ý chỉ 0,64–0,69, nên đặt 0,75 ở giữa. Kéo thanh ngưỡng cache ở Control panel xuống thấp thì sẽ HIT nhầm câu khác ý.

**Cache có gắn với từng hội thoại không?**
Không. Cache dùng chung cho mọi hội thoại và mọi user cùng role: lúc tra chỉ lọc `role` và 24 giờ, không lọc `conversationId` hay user. Câu user A hỏi ở chat này vẫn HIT cho user B ở chat khác.
- An toàn nhờ phía **ghi**: chỉ lưu câu trả lời không phụ thuộc ngữ cảnh (không gọi tool, có chunk tài liệu, không có tin nhắn trước trong memory).
- ⚠️ Phía **tra** chưa kiểm ngữ cảnh: câu hỏi tiếp nối kiểu "thế còn để được bao lâu?" về lý thuyết có thể HIT nhầm nếu điểm ≥ 0,75 (chưa đo). Production nên viết lại câu hỏi cho đứng độc lập trước khi tra (`RewriteQueryTransformer`), hoặc bỏ qua cache khi hội thoại đã có lịch sử.

**Admin hỏi cùng câu có dùng cache của User không?**
Không. Cache tách theo role (filter `role`), vì câu trả lời cho Admin và User có thể khác nhau.

**Khi nào cache bị xoá?**
Khi tri thức thay đổi (upload tài liệu, đổi chunk size, Reset), vì câu trả lời cũ dựa trên tài liệu cũ (`KnowledgeService.java:81`). Ngoài ra mỗi câu chỉ được dùng trong 24 giờ.

**Bảng không có cột thời gian, sao biết quá 24 giờ?**
Thời gian nằm trong cột `metadata` (JSON): lúc lưu, app ghi `createdAt` (epoch ms). Lúc tra, app gửi filter `role == USER AND createdAt >= bây giờ − 24h`; `PgVectorStore` dịch filter thành điều kiện SQL trên `metadata`, nên dòng quá hạn không được trả về.
- ⚠️ Dòng quá hạn chỉ bị **bỏ qua**, không bị xoá; bảng chỉ dọn khi tri thức thay đổi hoặc Reset. Production nên có job xoá:
  ```sql
  DELETE FROM semantic_cache
  WHERE (metadata->>'createdAt')::bigint < extract(epoch FROM now() - interval '24 hours') * 1000;
  ```

**HIT có lưu vào memory không?**
Có, nếu Memory bật: cache tự ghi cặp hỏi–đáp vào ChatMemory, vì memory advisor không chạy ở lượt HIT. Trong demo Memory tắt nên không ghi.

**Vì sao cache đứng ngoài cùng chuỗi?**
HIT thì trả lời luôn, bỏ qua memory, RAG và model; đứng ngoài cùng tiết kiệm được nhiều nhất.

### Nếu có sự cố
- **Lượt 2 vẫn MISS:** kiểm Cache và RAG đều bật ở lượt 1 (không có chunk thì không ghi), và ngưỡng cache ở Control panel là 0,75.
- **Lượt 1 đã HIT:** câu này đã được cache từ lần tập trước. Reset rồi chạy lại.

**Chuyển phần:** "Bot đã biết và trả lời nhanh, nhưng mới chỉ nói. Chưa làm được việc gì thật như xem tồn kho hay đặt đơn."

---

## 7. Tool calling + guardrail (~5 phút)

### Trên slide: `demo-assets/architecture.html` → tab **Tool calling**

**01 · @Tool**
- **Nói:** "Model chỉ sinh chữ: không tự đọc được giá, tồn kho, cũng không đặt được đơn. Tool cho model **nhờ app** gọi method Java để làm việc đó. Chỉ cần đánh dấu `@Tool`; Spring AI sinh JSON schema từ chữ ký method và mô tả."
- **Click** `</>`: `createOrder(product, quantity, ToolContext)`.

**02 · Gửi tool cùng prompt**
- **Nói:** "Mỗi request gửi kèm tên, mô tả và schema của 9 tool, để model chọn tool phù hợp."
- **Click** `</>`: `.tools(catalogTools, orderTools).toolContext(toolContext)`.

**03 · Vòng 1: `tool_use`**
- **Nói:** "Đây là câu trả lời cho câu hỏi 'model báo cho app biết nó muốn gọi tool bằng cách nào'. Model không chạy code; nó trả về `finishReason = tool_use` kèm tên tool và tham số JSON."

**04 · ToolCallingAdvisor chạy @Tool**
- **Nói:** "`ToolCallingAdvisor` đọc tool call, gọi `createOrder` với tham số model đưa. `OrderService` kiểm số lượng và tồn kho, trừ kho, lưu đơn PENDING."

**05 · Vòng 2: `tool_result` → `end_turn`**
- **Nói:** "Kết quả tool được gửi lại cho model trong lần gọi thứ hai. Model viết câu xác nhận và trả `end_turn`: vòng tool kết thúc."

### Trên bot: tab **User**, bấm **New chat**, bật **Memory + Tools** (RAG, Cache tắt)

**Lượt 1: "Cho mình đặt 2 chai Kefir Mật Ong 330ml"**
- **Mong đợi:** thẻ đơn **PENDING**, 2 × 38.000₫ = **76.000₫** (chạy thử: 7,5 s, $0,0096).
- **Behind the scenes, đọc từ trên xuống:**

| Dòng | Thấy gì |
|---|---|
| **Request 1** | `system · user`, **Tools: 9 tool** (`createOrder`, `listProducts`…) |
| **Response 1** | **`tool_use`**, Tool calls: `createOrder({"product": "Kefir Mật Ong 330ml", "quantity": 2})`, "ToolCallingAdvisor runs them and calls the model again" |
| **@Tool createOrder** | Arguments từ model → JSON của đơn (id, PENDING, 76000) |
| **Request 2** | `system · user · assistant · tool`: lời gọi tool và kết quả tool gửi lại cho model |
| **Response 2** | **`end_turn`**, không còn tool call: "the tool loop ends here" |
| **First chunk / Chunk N** | Chữ về **sau** vòng cuối, không chảy dần |

- **Nói:** "Cùng một câu hỏi, app gọi model **2 lần**. Lần 1 model chỉ nói 'tôi muốn gọi createOrder với tham số này'. App chạy method, gửi kết quả lại. Lần 2 model mới viết câu trả lời."

**Lượt 2: "Đổi thành 4 chai"**
- **Mong đợi:** đơn cập nhật 4 chai, 152.000₫. Inspector: tool `updateOrderQuantity(orderId, 4)`.
- **Nói:** "Câu này không có số đơn. Model lấy số đơn từ câu trả lời lượt 1 trong lịch sử (Memory). Memory chỉ lưu text, nên số đơn phải có trong câu trả lời."

**Lượt 3: "Doanh thu tháng 9 là bao nhiêu?"** (vẫn tab User)
- **Mong đợi:** bot giải thích chỉ quản trị viên xem được. Inspector: tool `getRevenueSummary` **có chạy**, kết quả là lỗi "Only the shop administrator can see revenue…".
- **Nói (guardrail):**
  - "Model vẫn gọi tool, nhưng bị chặn **trong code**: `requireAdmin()` đọc role từ `ToolContext`."
  - "`ToolContext` (userId, role) nằm phía server; model không thấy và không sửa được, nên không thể tự nhận là Admin hay đặt đơn hộ người khác."
  - "Quyền không dựa vào system prompt: prompt có thể bị lách, code thì không. Tool ném lỗi thì nội dung lỗi thành kết quả, model đọc và giải thích lại cho người dùng."

### Kiểm trong database

**Cách 1: mở bảng, lọc theo điều kiện**
- **`orders`**, lọc `username = 'an'` → đơn mới nhất PENDING, `total_amount` 152000 sau lượt 2.
- **`products`**, lọc `sku = 'MAT-ONG-330'` → `stock` giảm đúng số chai đang đặt (mặc định 24, sau lượt 2 còn 20).

**Cách 2: SQL**

```sql
SELECT o.id, o.status, o.total_amount, i.quantity, p.sku, p.stock
FROM orders o
JOIN order_items i ON i.order_id = o.id
JOIN products p ON p.id = i.product_id
WHERE o.username = 'an'
ORDER BY o.id DESC LIMIT 3;
```

### Tuỳ chọn (nếu dư thời gian)
- **"Đặt 500 chai Kefir Mật Ong 330ml"** → tool chạy, `OrderService` từ chối "The maximum is 20 per order…", model giải thích.
- **"Huỷ đơn vừa đặt"** → `cancelOrder`, kho được cộng lại.

### Nếu có người hỏi

**Model biết có tool nào bằng cách nào?**
Mỗi request gửi kèm tên, mô tả và JSON schema của từng tool, sinh từ `@Tool` và `@ToolParam`. Mô tả càng rõ thì model chọn tool càng đúng.

**Vì sao lượt có tool chữ không chảy dần?**
Đo thật: 20 chunk chữ về trong 12 ms ở cuối lượt. Khi có tool, `ToolCallingAdvisor` phải biết vòng đó có gọi tool hay không rồi mới cho đi tiếp, nên chữ chỉ về sau vòng cuối (suy ra từ số đo và bytecode Spring AI 2.0.1).

**Một lượt có tool tốn bao nhiêu?**
Khoảng $0,01: gọi model 2 lần, mỗi lần gửi kèm schema của 9 tool (3.758 token vào ở lần chạy thử).

**Model có thể gọi tool nào nó muốn không?**
Chỉ những tool được gửi trong request. Công tắc Tools tắt thì không gửi tool nào, model chỉ trả lời bằng chữ.

**Tool lỗi thì sao?**
Exception trong `@Tool` không làm sập lượt chat; Spring AI trả nội dung lỗi về cho model như kết quả tool, model đọc và giải thích.

**Memory có lưu lời gọi tool không?**
Không, chỉ lưu text hỏi/đáp. Vì vậy câu xác nhận phải có số đơn để lượt sau dùng được.

### Nếu có sự cố
- **Bot nói đã đặt nhưng không có thẻ đơn:** xem Inspector có dòng `@Tool createOrder` không; không có nghĩa là model không gọi tool, kiểm công tắc Tools đã bật.
- **Hết hàng:** Reset everything (kho về mặc định).
- **Trước buổi demo:** Reset everything, để xoá đơn từ các lần tập.

**Chuyển phần:** "Đến giờ kết quả luôn là text cho người đọc. Khi code cần dùng kết quả để hiện lên UI hay lưu DB thì text tự do không đủ."

---

## 8. Structured output (~3 phút)

### Trên slide: `demo-assets/architecture.html` → tab **Structured output**

**01 · Vì sao cần**
- **Nói:** "Đến giờ model luôn trả **text** cho người đọc. Nhưng khi kết quả cần cho **code dùng tiếp** (hiện từng ô trên UI, lưu DB), text tự do rất khó xử lý: mỗi lần model viết một kiểu. Giải pháp là khai báo một record làm khuôn."
- **Click** `</>`: `record RevenueBrief(headline, highlights, suggestion)`.

**02 · Gọi model với khuôn**
- **Nói:** "Chỉ cần `.responseEntity(converter)`. Spring AI sinh JSON schema từ record và dặn model chỉ trả JSON đúng khuôn. Số liệu doanh thu do Java tính sẵn; model chỉ viết nhận xét."
- **Click** `</>`: `BeanOutputConverter` và `.call().responseEntity(converter)`.

**03 · Nhận object**
- **Nói:** "Kết quả là object Java, dùng `brief.headline()` như mọi object khác. Model trả sai khuôn thì báo lỗi, nên luôn phải bắt lỗi."

### Trên bot: tab **Admin**, bấm **New chat**, bật **Tools**

1. Gõ **"Doanh thu 60 ngày qua thế nào?"**. Model gọi tool `getRevenueSummary` (flow Tool calling vừa xem), hiện **thẻ doanh thu**: khoảng ngày, tổng tiền, số đơn và số chai, so với kỳ trước, top sản phẩm.
2. Bấm **Generate brief** trên thẻ → **headline**, các **highlights**, khung **suggestion**, kèm thời gian, token, chi phí.
3. Mở **"How Spring AI did it"** trên thẻ:
   - **JSON schema:** Spring AI sinh từ record và gửi kèm prompt.
   - **Raw:** chuỗi JSON **thô** model trả về, trước khi thành object.
4. **Nói:** "Đây là JSON thô model trả về. Spring AI chuyển nó thành `RevenueBrief`, và UI hiện từng trường vào đúng chỗ: headline, highlights, suggestion. Không cần cắt chuỗi."

Lượt Generate brief gọi qua REST riêng, không qua chat, nên **Inspector không hiện**; panel "How Spring AI did it" thay vai trò đó.

### Nếu có người hỏi

**Structured output khác gì tool calling?**
Tool: model **xin** app chạy code (`tool_use`). Structured output: model **trả** dữ liệu theo khuôn cho app. Tool calling gọi model 2 lần, structured output chỉ 1 lần.

**Khi nào cần dùng?**
Khi kết quả AI được **code dùng tiếp** (UI, DB, API khác). Câu trả lời chat cho người đọc thì text là đủ.

**Vì sao số liệu không để model tính?**
Model không đáng tin khi làm phép tính. Java tính chính xác; model chỉ viết nhận xét từ số đã có, và system prompt dặn "không bịa số".

**Vì sao dùng `call()` mà không dùng `stream()`?**
Phải có đủ chuỗi JSON mới chuyển thành object được; stream từng mẩu không giúp gì.

**Vì sao ChatClient trơn, không advisor?**
Brief chỉ cần số liệu đưa vào; memory, RAG hay tool chỉ thêm token mà không thêm giá trị.

**Model trả sai JSON thì sao?**
Không parse được thì endpoint trả **502** "did not return a usable brief". Code luôn phải bắt lỗi này.

**Có tốn tiền không?**
Có, như mọi lần gọi model, và được ghi vào ledger như lượt chat.

### Nếu có sự cố
- **Không có thẻ doanh thu:** kiểm đang ở tab **Admin** và Tools đã bật (User hỏi thì bị chặn, như phần Tool calling).
- **Generate brief báo 502:** model trả sai khuôn; bấm lại.

**Chuyển phần:** "Đã có nhiều tính năng. Câu hỏi còn lại: đổi prompt, đổi model hay thêm tài liệu thì làm sao biết bot không trả lời sai?"

---

## 9. Testing: chấm câu trả lời AI (~2 phút, chỉ slide)

### Trên slide: `demo-assets/architecture.html` → tab **Testing**

**01 · Vì sao khó test AI**
- **Nói:** "Code bình thường thì cùng input cho cùng output, nên `assertEquals` được. Model thì mỗi lần trả lời một kiểu chữ: hôm nay 'dưới 3 ngày', mai 'tối đa 3 ngày'. Không so bằng được. Spring AI giải quyết bằng **Evaluator**: dùng thêm một lần gọi model để **chấm** câu trả lời và trả về pass/fail. Cách này gọi là LLM-as-a-judge."
- **Nói thêm:** "Test hỏi chat thật, lấy câu trả lời cùng các chunk RAG, rồi mới chấm."

**02 · RelevancyEvaluator**
- **Nói:** "Evaluator đầu tiên kiểm **câu trả lời có đúng trọng tâm không**. Đưa cho model chấm 3 thứ: câu hỏi, các chunk tri thức, và câu trả lời. Model chấm trả lời YES hoặc NO."
- **Click** `</>`: `RelevancyEvaluator.builder()… .evaluate(new EvaluationRequest(QUESTION, retrieved, reply.answer()))`.

**03 · FactCheckingEvaluator**
- **Nói:** "Evaluator thứ hai kiểm **câu trả lời có dựa trên tài liệu không**, tức là có bịa không. Câu trả lời thật của bot thì pass. Đưa vào một câu bịa 'Kefir Mật Ong để được 30 ngày' thì cùng evaluator đó chấm fail: nó bắt được hallucination."
- **Click** `</>`: hai đoạn: câu trả lời thật, và câu bịa "30 ngày".

### Nếu có người hỏi

**Model chấm có đáng tin không?**
Không tuyệt đối, vì judge cũng là model. Evaluator dùng để **phát hiện sớm** câu trả lời lệch hoặc bịa khi đổi prompt, model hay tài liệu, chứ không thay được người kiểm.

**Có bài học gì khi dùng không?**
Có. Evaluator mặc định của Spring AI chỉ nhận đúng chữ `yes`. Model trả "YES" kèm đoạn giải thích, nên bị chấm **fail** dù câu trả lời đúng. Cách sửa: thêm vào prompt chấm "Reply with exactly one word, YES or NO, and nothing else" (`promptTemplate(...)` / `evaluationPrompt(...)`).

**Chạy test này có tốn tiền không?**
Có: mỗi test gọi model thật vài lần, khoảng $0,01 cho cả bộ, mất khoảng 25 s. Vì vậy test được gắn `@Tag("live")` và mặc định không chạy cùng các test thường; muốn chạy phải bật riêng.

**Hai evaluator khác nhau thế nào?**
- `RelevancyEvaluator`: câu trả lời có **trả lời đúng câu hỏi** không (cần câu hỏi, tri thức, câu trả lời).
- `FactCheckingEvaluator`: câu trả lời có **đúng với tài liệu** không (cần tri thức, câu trả lời).
- Một câu trả lời có thể đúng chủ đề mà vẫn bịa số liệu, nên dùng cả hai.

**Có viết evaluator riêng được không?**
Được. `Evaluator` là interface chỉ có một method `evaluate()`, nên có thể tự viết kiểm tra riêng cho dự án.

Lần gần nhất 4 test live xanh là chạy với **Haiku**; profile `demo` giờ là Sonnet và chưa chạy lại. Tránh nói "đã chạy xanh với Sonnet".

**Chuyển phần:** "Tool hiện chỉ chatbot trong app dùng được. Phần cuối: cho AI client khác dùng lại chính các tool đó."

---

## 10. MCP (~3 phút, tuỳ chọn)

### Trên slide: `demo-assets/architecture.html` → tab **MCP**

**01 · MCP là gì**
- **Nói:** "Đến giờ chỉ chatbot của app gọi được tool. Model Context Protocol là chuẩn mở để **AI client bên ngoài**, như Claude Code hay Claude Desktop, gọi tool của ứng dụng. Chỉ cần thêm starter `spring-ai-starter-mcp-server-webmvc`; app thành MCP server ở endpoint `/mcp`."
- **Click** `</>`: starter trong `pom.xml`, yaml `name: kefir`, `protocol: STREAMABLE`.

**02 · Đăng ký tool**
- **Nói:** "Không viết thêm logic: dùng lại đúng các `@Tool` của chat. Chỉ đăng ký 3 tool **chỉ đọc**: `listProducts`, `getProduct`, `searchKnowledge`. Client gọi tool giống chatbot gọi, chạy qua cùng service."
- **Click** `</>`: `MethodToolCallbackProvider.builder().toolObjects(catalog, knowledge)`.
- **Nói thêm (bảo mật):** "Không có tool đặt đơn hay doanh thu, vì MCP không đi qua đăng nhập User/Admin. Và `/mcp` chỉ mở cho client **trên cùng máy**; trang web bị chặn qua header `Origin`."

### Demo: Claude Code bằng **tài khoản Claude cá nhân** trên máy demo

Máy BILL chặn MCP tuỳ chỉnh (`not allowed by enterprise policy`), nên chỉ demo được trên máy dùng tài khoản cá nhân. **Không kết nối được thì chỉ trình bày slide.**

1. App đang chạy trên **cùng máy** (http://localhost:8080).
2. Trong terminal, thêm server:

```bash
claude mcp add --transport http kefir http://localhost:8080/mcp
```

3. Kiểm tra kết nối:

```bash
claude mcp list
```

Mong đợi: `kefir` hiện là đã kết nối. Trong Claude Code, gõ `/mcp` để xem server `kefir` và 3 tool.

4. Hỏi Claude Code:
   - **"Kefir đang còn những vị nào?"** → Claude gọi `listProducts`, liệt kê sản phẩm, giá, tồn kho.
   - **"Kefir mật ong bảo quản bao lâu?"** → Claude gọi `searchKnowledge`, trả lời "dưới 3 ngày" kèm nguồn.
5. **Nói:** "Cùng method `@Tool` mà chatbot dùng, giờ một AI khác gọi được, không viết thêm dòng logic nào."
6. Gỡ sau khi demo:

```bash
claude mcp remove kefir
```

### Checklist thử trên máy Windows (trước buổi demo)
- [ ] Claude Code đã cài và **đăng nhập tài khoản cá nhân** (không phải tài khoản BILL).
- [ ] App chạy được trên máy Windows, mở http://localhost:8080 được.
- [ ] `claude mcp add …` không báo lỗi policy.
- [ ] `claude mcp list` / `/mcp` thấy `kefir` và 3 tool.
- [ ] Hai câu hỏi ở bước 4 đều gọi đúng tool và trả lời được.
- [ ] `claude mcp remove kefir` gỡ được.
- Nếu một bước hỏng: bỏ demo, chỉ trình bày slide.

### Nếu có người hỏi

**Vì sao chỉ có tool đọc?**
MCP không đi qua đăng nhập User/Admin, nên không có `ToolContext` với người dùng thật. Mọi tool ghi dữ liệu (đặt, sửa, huỷ đơn) và tool Admin (doanh thu) không được đăng ký cho MCP.

**Ai gọi được `/mcp`?**
Chỉ client trên **cùng máy** (địa chỉ 127.0.0.1 / ::1). Nếu request có header `Origin` thì Origin phải là localhost; nhờ vậy một trang web lạ (hoặc DNS rebinding) không gọi được dù chạy trên chính máy người dùng. Client dòng lệnh như Claude Code không gửi `Origin`.

**Có bài học gì khi làm?**
Starter mặc định mở transport SSE ở `/sse`, và rule bảo mật ban đầu chỉ chặn `/mcp/**`, nên `/sse` từng mở cho mọi người. App đã chuyển sang `protocol: STREAMABLE` (endpoint `/mcp`) và chặn cả `/mcp/**`, `/sse`, `/sse/**`.

**MCP khác gì tool calling trong chat?**
Cùng một method `@Tool`. Khác ở **ai là model**: trong chat là Claude do app gọi; với MCP là AI client bên ngoài tự chọn và gọi tool qua giao thức chuẩn.

**Đã test chưa?**
Có test tích hợp `McpServerIntegrationTest` chạy `initialize → tools/list → tools/call` qua HTTP thật. Demo với client thật cần máy dùng tài khoản cá nhân.

---

## Kết thúc (~2 phút)

**Tóm lại theo mạch:**
| Nhu cầu | Spring AI |
|---|---|
| Gọi model, đổi nhà cung cấp | `ChatModel`, `ChatClient` |
| Nhớ hội thoại | `ChatMemory` + advisor |
| Trả lời từ tài liệu | ETL, `VectorStore`, `QuestionAnswerAdvisor` |
| Làm việc thật | `@Tool` |
| Dữ liệu cho code | structured output |
| Kiểm chất lượng | `Evaluator` |
| Mở cho AI khác | MCP server |

**3 ý mang về:**
- Spring AI là cách làm AI theo chuẩn Spring: interface chung, đổi bằng cấu hình, dùng như mọi bean khác.
- **Advisor** là trung tâm: memory, RAG, cache đều gắn vào cùng một chỗ, không sửa code gọi model.
- Muốn đưa AI lên production thì phải **quan sát được** (observability, Inspector) và **test được** (Evaluator).

**Nói rõ phần nào app tự viết:** semantic cache, router, Inspector. Các phần còn lại dùng thẳng Spring AI.

Mời hỏi đáp.
