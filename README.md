
# Pison - SIMD-Accelerated JSON Indexing in Java

**Pison** is a Java-based structural indexing engine for JSON records, inspired by the original [Pison C++ implementation](https://vldb.org/pvldb/vol14/p694-jiang.pdf). This project enables efficient querying and traversal of large or deeply nested JSON structures using SIMD and multithreaded parallelism.

---

## 📌 Features

- SIMD and bitwise parallelism for rapid JSON index construction
- Multi-level bitmap indexing (for `,` and `:`)
- Efficient traversal using iterators (`BitmapIterator`)
- Supports large single-record JSON files (e.g., Best Buy, Twitter)
- Java port with custom JSON query API

---

## 📂 Project Structure

```
.
├── datasets/
│   ├── sample.json
│   └── bestbuy.json
├── src/main/java/com/group8/pison/
│   ├── demo/                # Examples and test queries
│   ├── index/               # Bitmap construction logic
│   ├── iterator/            # JSON traversal logic
│   ├── util/                # SIMD utilities and memory management
│   └── Pison.java           # Main entry point
├── pom.xml                  # Maven build file
└── README.md
```

---

## ⚙️ Getting Started

### Prerequisites

- Java 17+ (with support for `jdk.incubator.vector`)
- Maven 3.6+
- Mac/Linux system with a CPU supporting AVX2 (SIMD)

---

## 🏗️ Build

```bash
mvn clean install
```

---

## ▶️ Run

To query `sample.json` using a demo:

```bash
mvn exec:java -Dexec.mainClass="com.group8.pison.demo.PisonDemo"
```

For other datasets like `bestbuy.json`:

```bash
mvn exec:java -Dexec.mainClass="com.group8.pison.demo.PisonDemo3" -Dexec.args="datasets/bestbuy.json"
```

---

## 💡 Example Query

**Objective**: Get the value of `user[1].name` from `sample.json`.

```java
BitmapIterator it = pison.iterator();
if (it.isObject() && it.moveToKey("user")) {
    it.down(); // into array
    if (it.isArray() && it.moveToIndex(1)) {
        it.down(); // into user[1]
        if (it.isObject() && it.moveToKey("name")) {
            String name = it.getValue();
            System.out.println("Name: " + name);
        }
    }
}
```

---

## 📈 Performance

Our implementation builds multi-level bitmap indexes using:

- Parallel construction via Java ForkJoinPool
- SIMD operations via `jdk.incubator.vector` module

Benchmarked on:
- **MacBook Air (Apple Silicon)**
- **JSON sizes**: up to 1GB (e.g., BestBuy product catalog)

---

## 📘 References

- [Pison (PVLDB'21 Paper)](https://vldb.org/pvldb/vol14/p694-jiang.pdf)
- [Mison (VLDB'17)](https://vldb.org/pvldb/vol10/p1118-li.pdf)
- [simdjson](https://arxiv.org/abs/1902.08318)

---

## 📜 License

This project is for academic and research purposes.

---

## 👨‍💻 Authors

- Gautham C
- Pankaj Chowdary
- Nithya 
- Based on research by Lin Jiang, Junqiao Qiu, Zhijia Zhao
