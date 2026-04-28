# ConvertDataToShp

CSV / Excel 与 Shapefile 格式互转工具。

## 功能

- **CSV → SHP**：将 CSV 文件转换为 Shapefile 格式
- **Excel → SHP**：将 Excel (.xls/.xlsx) 文件转换为 Shapefile 格式
- **SHP → CSV**：将 Shapefile 转换为 CSV 文件
- **SHP → Excel**：将 Shapefile 转换为 Excel 文件
- 支持 GBK / UTF-8 / GB2312 编码

## 快速开始

### 1. 构建项目

```bash
mvn clean package
```

### 2. 启动服务

```bash
java -jar target/ConvertDataToShp-1.0-SNAPSHOT.jar
```

或使用 Maven：

```bash
mvn spring-boot:run
```

### 3. 访问 Web 界面

浏览器打开 http://localhost:8080

## 使用说明

### CSV / Excel 转 SHP

1. 选择要转换的 CSV 或 Excel 文件
2. 选择文件编码（与原文件编码一致）
3. 系统自动检测几何列（WKT 格式，如 `POINT (116.4 39.6)`）
4. 也可手动指定几何列名
5. 点击转换，下载生成的 ZIP 包（含 .shp/.shx/.dbf/.prj/.cpg）

### SHP 转 CSV / Excel

1. 选择完整的 Shapefile 文件（.shp, .shx, .dbf, .prj, .cpg）
2. 可多选文件同时上传
3. 选择输出格式和编码
4. 点击转换，下载转换后的文件

### 编码说明

| 原始文件编码 | 应选择编码 |
|------------|-----------|
| UTF-8      | UTF-8     |
| GBK        | GBK       |
| GB2312     | GB2312    |

编码选择错误会导致中文乱码。

## 项目结构

```
src/main/java/com/hgx/
├── Application.java              # CLI 入口
├── ShpConverterApplication.java # Spring Boot 启动类
├── controller/
│   └── ConverterController.java   # Web API 控制器
├── service/
│   └── ConverterService.java      # 转换服务核心逻辑
├── converter/
│   ├── DataReader.java           # 数据读取接口
│   ├── DataWriter.java           # 数据写入接口
│   ├── WktGeometryParser.java    # WKT 几何解析器
│   ├── GeometryColumnDetector.java# 几何列自动检测
│   ├── toshape/                  # CSV/Excel → SHP
│   │   ├── CsvDataReader.java
│   │   ├── ExcelDataReader.java
│   │   └── ShapefileWriter.java
│   └── fromshape/                # SHP → CSV/Excel
│       ├── ShapefileReader.java
│       ├── CsvDataWriter.java
│       └── ExcelDataWriter.java
└── model/
    ├── FeatureData.java          # 要素数据模型
    └── GeometryType.java         # 几何类型枚举
```

## 技术栈

- **Spring Boot 3.2.5** — Web 框架
- **GeoTools 28.2** — Shapefile 读写
- **Apache POI 5.2.5** — Excel 文件处理
- **OpenCSV 5.9** — CSV 文件处理
- **Picocli 4.7.5** — CLI 命令行支持
