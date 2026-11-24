-- ============================================================
-- 星选商城 · MySQL 种子数据（幂等：已存在则跳过，重启不会重复插入或覆盖数据）
-- 密码统一为 123456 的 BCrypt 哈希：
--   $2b$10$DYdfWxCy43FxL1kxaaJnje.dm5o.GzfWarxKz9A4y32E.x7eH52rq
--
-- 用户业务示例数据（订单 / 评价 / 收藏 / 地址）只在首次初始化时写入：
-- 之后管理员「重置数据」删掉的示例数据不会因重启被重新塞回来。
-- ============================================================
SET @seed_user_data := (SELECT COUNT(*) FROM seed_meta WHERE id = 1);

-- ---------------- 分类 ----------------
INSERT INTO category (id, name, icon, sort_order) SELECT 1, '手机数码', 'phone',    1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE id = 1);
INSERT INTO category (id, name, icon, sort_order) SELECT 2, '电脑办公', 'computer', 2 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE id = 2);
INSERT INTO category (id, name, icon, sort_order) SELECT 3, '家用电器', 'home',     3 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE id = 3);
INSERT INTO category (id, name, icon, sort_order) SELECT 4, '服饰鞋包', 'shirt',    4 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE id = 4);
INSERT INTO category (id, name, icon, sort_order) SELECT 5, '美妆个护', 'lipstick', 5 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE id = 5);
INSERT INTO category (id, name, icon, sort_order) SELECT 6, '图书音像', 'book',     6 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM category WHERE id = 6);

-- ---------------- 用户（密码统一 123456） ----------------
INSERT INTO user_table (id, username, password, nickname, email, role)
  SELECT 1, 'admin',   '$2b$10$DYdfWxCy43FxL1kxaaJnje.dm5o.GzfWarxKz9A4y32E.x7eH52rq', '超级管理员', 'admin@demo.com',   'ADMIN'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_table WHERE username = 'admin');
INSERT INTO user_table (id, username, password, nickname, email, role)
  SELECT 2, 'alice',   '$2b$10$DYdfWxCy43FxL1kxaaJnje.dm5o.GzfWarxKz9A4y32E.x7eH52rq', '爱丽丝',     'alice@demo.com',   'USER'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_table WHERE username = 'alice');
INSERT INTO user_table (id, username, password, nickname, email, role)
  SELECT 3, 'bob',     '$2b$10$DYdfWxCy43FxL1kxaaJnje.dm5o.GzfWarxKz9A4y32E.x7eH52rq', '鲍勃',       'bob@demo.com',     'USER'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_table WHERE username = 'bob');
INSERT INTO user_table (id, username, password, nickname, email, role)
  SELECT 4, 'charlie', '$2b$10$DYdfWxCy43FxL1kxaaJnje.dm5o.GzfWarxKz9A4y32E.x7eH52rq', '查理',       'charlie@demo.com', 'USER'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_table WHERE username = 'charlie');
INSERT INTO user_table (id, username, password, nickname, email, role)
  SELECT 5, 'david',   '$2b$10$DYdfWxCy43FxL1kxaaJnje.dm5o.GzfWarxKz9A4y32E.x7eH52rq', '戴维',       'david@demo.com',   'USER'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_table WHERE username = 'david');

-- ---------------- 商品 ----------------
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 1, 'iPhone 15 Pro 256GB 5G 手机', 'Apple iPhone 15 Pro 256GB 钛原色', 8999.00, 50,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20premium%20Apple%20iPhone%2015%20Pro%20smartphone%20in%20natural%20titanium%20color%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    1, 120 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 1);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 2, '小米 14 Ultra 5G 手机', '徕卡光学 Summilux 镜头', 6499.00, 80,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20Xiaomi%2014%20Ultra%20smartphone%20with%20Leica%20camera%20module%2C%20sleek%20modern%20design%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    1, 88 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 2);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 3, '华为 Mate60 Pro 手机', '玄武架构 卫星通话', 6999.00, 60,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20Huawei%20Mate%2060%20Pro%20smartphone%20in%20classic%20black%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    1, 200 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 3);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 4, 'MacBook Pro 14 M3', 'Apple M3 16GB 512GB 深空灰', 14999.00, 30,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=An%20Apple%20MacBook%20Pro%2014%20inch%20M3%20laptop%20in%20space%20gray%20opened%20at%20angle%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    2, 45 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 4);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 5, 'ThinkPad X1 Carbon', '13代i7 16G 512G 2.8K', 12999.00, 25,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20Lenovo%20ThinkPad%20X1%20Carbon%20laptop%20in%20matte%20black%20with%20the%20classic%20red%20TrackPoint%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    2, 33 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 5);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 6, '戴尔 XPS 15', '13代i9 32G 1T RTX4060', 18999.00, 15,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20Dell%20XPS%2015%20laptop%20with%20slim%20bezel%20display%20in%20silver%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    2, 12 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 6);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 7, '戴森 V12 吸尘器', '无线手持 大吸力', 4990.00, 40,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20Dyson%20V12%20cordless%20stick%20vacuum%20cleaner%20in%20yellow%20and%20gray%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    3, 67 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 7);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 8, '美的 1.5匹空调', '新一级能效 智能变频', 3299.00, 100,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20modern%20white%20wall-mounted%20split%20air%20conditioner%20unit%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    3, 156 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 8);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 9, '小米电视 6 OLED', '65英寸 4K HDR', 6999.00, 35,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20modern%20OLED%20flat%20screen%20television%2065%20inch%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    3, 78 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 9);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 10, '优衣库摇粒绒外套', '保暖透气 男女同款', 299.00, 500,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20soft%20beige%20fleece%20jacket%20folded%20neatly%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    4, 999 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 10);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 11, '阿迪达斯 Ultraboost', '透气网面 缓震跑鞋', 1299.00, 200,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20pair%20of%20white%20and%20gray%20Adidas%20Ultraboost%20running%20shoes%20with%20boost%20sole%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    4, 333 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 11);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 12, '蔻驰女士手提包', '经典款 时尚百搭', 1880.00, 60,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20classic%20tan%20brown%20leather%20women%27s%20handbag%20with%20gold%20hardware%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    4, 102 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 12);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 13, '雅诗兰黛小棕瓶', '修护精华 50ml', 980.00, 80,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=An%20Estee%20Lauder%20Advanced%20Night%20Repair%20serum%20bottle%2C%20the%20iconic%20small%20brown%20glass%20bottle%20with%20gold%20cap%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    5, 234 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 13);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 14, 'SK-II 神仙水', 'pitera 精华 230ml', 1690.00, 50,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20SK-II%20Facial%20Treatment%20Essence%20bottle%2C%20frosted%20white%20glass%20bottle%20with%20red%20logo%2C%20studio%20lighting%2C%20minimal%20product%20photography%2C%20editorial%20style%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    5, 178 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 14);

INSERT INTO product (id, name, description, price, stock, cover, category_id, sales)
  SELECT 15, '三体全集', '刘慈欣 著 重庆出版社', 99.00, 1000,
    'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=A%20stack%20of%20three%20sci-fi%20novels%20with%20minimalist%20dark%20book%20cover%20design%2C%20editorial%20book%20photography%2C%20studio%20lighting%2C%20clean%20cream%20background%2C%20no%20text&image_size=square_hd',
    6, 567 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 15);

-- ---------------- 商品（批量补充，共 100 件；无自定义图时使用分类配图 /img/cat-{cid}.jpg） ----------------
-- 手机数码（16-29）
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 16, 'iPad Pro 11 M4', 'M4芯片 256GB WLAN版', 8999.00, 40, NULL, 1, 76 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 16);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 17, 'AirPods Pro 2', 'USB-C接口 主动降噪', 1899.00, 300, NULL, 1, 534 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 17);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 18, 'Apple Watch S9', '45mm GPS版 午夜色', 2999.00, 120, NULL, 1, 210 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 18);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 19, '索尼 WH-1000XM5', '头戴式降噪耳机 铂金银', 2399.00, 90, NULL, 1, 188 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 19);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 20, '索尼 A7M4 微单', '全画幅 3300万像素 28-70套机', 16999.00, 12, NULL, 1, 23 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 20);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 21, '大疆 Mini 4 Pro', '全能迷你航拍机 畅飞套装', 6788.00, 35, NULL, 1, 41 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 21);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 22, 'Switch OLED 日版', '白色 Joy-Con 掌机', 2099.00, 150, NULL, 1, 265 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 22);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 23, 'Kindle Paperwhite 5', '8G 6.8英寸 墨水屏', 998.00, 200, NULL, 1, 312 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 23);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 24, '小米手环 8 Pro', '1.74英寸大屏 14天续航', 399.00, 500, NULL, 1, 876 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 24);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 25, 'OPPO Find X7 5G 手机', '天玑9300 哈苏影像 16+512', 4499.00, 70, NULL, 1, 65 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 25);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 26, 'vivo X100 Pro 5G 手机', '蔡司APO超级长焦 16+512', 4999.00, 65, NULL, 1, 58 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 26);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 27, '一加 12 5G 手机', '骁龙8Gen3 2K东方屏 24+512', 4799.00, 55, NULL, 1, 72 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 27);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 28, '安克 65W 氮化镓充电器', '三口快充 可折叠插脚', 149.00, 800, NULL, 1, 1240 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 28);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 29, '闪迪 1TB Type-C 移动固态', '读取1050MB/s 三防', 749.00, 260, NULL, 1, 318 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 29);

-- 电脑办公（30-43）
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 30, 'iPad Air 5', 'M1芯片 64GB WLAN版', 3799.00, 85, NULL, 2, 143 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 30);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 31, '罗技 MX Master 3S', '无线鼠标 8K DPI 静音按键', 799.00, 180, NULL, 2, 421 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 31);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 32, '罗技 MX Keys S', '无线键盘 智能背光', 899.00, 140, NULL, 2, 233 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 32);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 33, '戴尔 U2723QE 显示器', '27英寸4K IPS Black Type-C 90W', 4299.00, 45, NULL, 2, 87 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 33);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 34, 'LG 27GR93U 显示器', '27英寸 4K 144Hz Nano IPS', 3599.00, 38, NULL, 2, 54 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 34);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 35, 'iPad Pro 巧控键盘', '11英寸 英文键盘', 2399.00, 30, NULL, 2, 26 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 35);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 36, '西部数据 4TB 移动硬盘', 'USB3.2 Elements系列', 899.00, 220, NULL, 2, 356 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 36);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 37, '罗技 C920c 摄像头', '1080P 自动对焦 双麦克风', 399.00, 160, NULL, 2, 198 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 37);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 38, '绿联 12合1 扩展坞', '双HDMI 4K60 网口 PD100W', 399.00, 300, NULL, 2, 402 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 38);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 39, '惠普 LaserJet 打印机', '黑白激光无线打印 M1136', 1299.00, 60, NULL, 2, 91 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 39);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 40, '得力会议白板', '90×120cm 磁性挂式', 259.00, 90, NULL, 2, 44 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 40);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 41, '晨光按动中性笔 20支', '0.5mm 全针管 作业帮联名', 29.90, 1500, NULL, 2, 2100 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 41);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 42, '雷蛇猎魂光蛛 V3 Pro', '光学轴体 无线电竞键盘', 1899.00, 42, NULL, 2, 37 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 42);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 43, 'Herman Miller Aeron 人体工学椅', '网面透气 尺寸B 可调腰托', 12800.00, 8, NULL, 2, 11 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 43);

-- 家用电器（44-57）
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 44, '戴森 V15 Detect', '无绳吸尘器 激光探尘', 4590.00, 50, NULL, 3, 96 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 44);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 45, '戴森 HD15 吹风机', '智能温控 紫红色镍色', 3199.00, 75, NULL, 3, 154 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 45);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 46, '石头 G20S 扫地机器人', '双胶刷 10000Pa 吸力', 5499.00, 40, NULL, 3, 68 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 46);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 47, '科沃斯 T30 Pro', '扫拖一体 恒压活水洗', 3999.00, 55, NULL, 3, 82 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 47);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 48, '美的 1.5匹 空调挂机', '新一级能效 变频冷暖', 2699.00, 85, NULL, 3, 137 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 48);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 49, '海尔 10kg 滚筒洗衣机', '直驱变频 除菌螨', 3299.00, 48, NULL, 3, 74 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 49);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 50, '容声 501L 对开门冰箱', '风冷无霜 双变频一级', 3599.00, 36, NULL, 3, 52 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 50);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 51, '米家空气净化器 Pro', '500m³/h CADR OLED屏', 1499.00, 110, NULL, 3, 246 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 51);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 52, '九阳豆浆机', '1.3L 免滤免洗 破壁', 599.00, 130, NULL, 3, 312 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 52);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 53, '苏泊尔电饭煲', '4L IH电磁加热 远红外内胆', 499.00, 160, NULL, 3, 289 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 53);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 54, '摩飞多功能锅', '6合1 分体式料理锅', 899.00, 95, NULL, 3, 176 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 54);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 55, '小米电视 ES65', '65英寸4K 120Hzmemc', 3299.00, 42, NULL, 3, 63 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 55);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 56, '飞利浦电动牙刷 HX6730', '声波震动 三种模式', 399.00, 240, NULL, 3, 428 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 56);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 57, '松下纳诺怡吹风机', '纳米水离子 护发', 899.00, 100, NULL, 3, 133 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 57);

-- 服饰鞋包（58-71）
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 58, '优衣库 摇粒绒立领外套', '男款 立领 拉链保暖', 299.00, 400, NULL, 4, 671 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 58);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 59, '阿迪达斯 Ultraboost Light', '轻量版 跑步鞋 Boost中底', 1299.00, 150, NULL, 4, 342 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 59);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 60, 'Nike Air Force 1', '经典空军一号 白色', 899.00, 210, NULL, 4, 489 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 60);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 61, '优衣库宽松卫衣', '纯棉 落肩廓形 米白', 199.00, 350, NULL, 4, 528 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 61);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 62, '李宁 运动长裤', '男子束脚卫裤 冰丝', 249.00, 280, NULL, 4, 316 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 62);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 63, 'New Balance 574', '经典复古跑步鞋 灰蓝', 669.00, 175, NULL, 4, 254 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 63);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 64, '北面 1996 复古羽绒服', '700蓬鹅绒 黑色', 3298.00, 60, NULL, 4, 128 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 64);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 65, '始祖鸟 Beta LT 冲锋衣', 'GORE-TEX 三层压胶', 4200.00, 25, NULL, 4, 47 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 65);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 66, 'Coach 老花托特包', 'PVC皮革 女款手提包', 2950.00, 45, NULL, 4, 39 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 66);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 67, 'Samsonite 新秀丽背包', '商务双肩 15.6英寸防泼水', 899.00, 120, NULL, 4, 187 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 67);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 68, 'Levi''s 501 直筒牛仔裤', '经典款 中蓝 32码', 799.00, 160, NULL, 4, 209 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 68);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 69, 'Casio G-SHOCK 手表', 'GA-2100 农家橡树 黑', 1090.00, 90, NULL, 4, 143 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 69);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 70, '波司登轻暖羽绒服', '女款 90绒 轻薄便携', 1299.00, 140, NULL, 4, 231 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 70);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 71, '海澜之家商务衬衫', '免烫抗皱 长袖 白色', 199.00, 320, NULL, 4, 401 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 71);

-- 美妆个护（72-85）
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 72, 'SK-II 神仙水 230ml', '面部护肤精华液', 1590.00, 80, NULL, 5, 267 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 72);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 73, '兰蔻小黑瓶精华', '第二代肌底液 100ml', 1580.00, 65, NULL, 5, 198 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 73);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 74, '雅诗兰黛小棕瓶 100ml 双支装', '第七代修护精华 双支礼盒', 1650.00, 70, NULL, 5, 223 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 74);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 75, '欧莱雅紫熨斗眼霜', '玻色因 30ml', 340.00, 190, NULL, 5, 476 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 75);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 76, '资生堂红腰子精华', '红妍肌活 75ml', 1080.00, 55, NULL, 5, 154 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 76);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 77, '理肤泉 B5 修复霜', '40ml 舒缓修护', 189.00, 260, NULL, 5, 387 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 77);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 78, '安热沙金瓶防晒', 'SPF50+ 60ml', 338.00, 210, NULL, 5, 512 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 78);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 79, 'MAC 子弹头口红', 'CHILI 小辣椒色 3g', 230.00, 240, NULL, 5, 598 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 79);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 80, 'YSL 自由之水香水', 'EDP 50ml', 1050.00, 60, NULL, 5, 132 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 80);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 81, '花知晓独角兽腮红', '丝绒哑光 6g', 129.00, 300, NULL, 5, 445 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 81);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 82, '戴珂紫苏水', '150ml 控油舒缓', 420.00, 130, NULL, 5, 219 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 82);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 83, '施华蔻染发剂', '植物无氨 3.0羊绒脂深棕', 89.00, 400, NULL, 5, 623 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 83);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 84, '凡士林身体乳', '烟酰胺 250ml', 79.00, 600, NULL, 5, 890 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 84);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 85, 'rio 香体喷雾', '50ml 花果香 便携', 69.00, 450, NULL, 5, 376 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 85);

-- 图书音像（86-100）
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 86, '球状闪电', '刘慈欣 著 四川科学技术出版社', 39.50, 650, NULL, 6, 1567 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 86);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 87, '活着', '余华 著 作家出版社', 45.00, 800, NULL, 6, 2103 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 87);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 88, '百年孤独', '马尔克斯 著 南海出版公司', 55.00, 600, NULL, 6, 1432 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 88);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 89, '红楼梦（人民文学版）', '曹雪芹 著 上下册', 59.70, 500, NULL, 6, 986 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 89);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 90, '人类简史', '尤瓦尔·赫拉利 著 中信出版社', 68.00, 450, NULL, 6, 764 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 90);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 91, '深入理解计算机系统', 'CSAPP 第三版 机械工业出版社', 139.00, 300, NULL, 6, 542 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 91);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 92, '算法（第4版）', 'Sedgewick 著 人民邮电出版社', 99.00, 260, NULL, 6, 381 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 92);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 93, 'JavaScript高级程序设计', '红宝书第4版 人民邮电出版社', 129.00, 320, NULL, 6, 618 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 93);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 94, '明朝那些事儿全集', '当年明月 著 9册', 258.00, 200, NULL, 6, 727 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 94);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 95, '小王子', '中英法三语 译文出版社', 32.00, 700, NULL, 6, 1156 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 95);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 96, '流行音乐精选黑胶', '180g 12寸 LP 唱片', 199.00, 150, NULL, 6, 89 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 96);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 97, '周杰伦《范特西》黑胶', '复刻版 180g LP', 299.00, 100, NULL, 6, 143 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 97);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 98, 'BJ40 无线蓝牙音箱', '便携防水 户外音响', 499.00, 180, NULL, 1, 267 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 98);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 99, '经济学原理（曼昆）', '微观+宏观 第8版 北大出版社', 98.00, 240, NULL, 6, 302 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 99);
INSERT INTO product (id, name, description, price, stock, cover, category_id, sales) SELECT 100, '2026考研英语一本通', '真题解析+词汇 高教社', 89.00, 350, NULL, 6, 412 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM product WHERE id = 100);

-- 封面本地化：历史种子数据中的外部生成图链接已失效，与空值一并替换为分类配图
-- （前端 public/img/cat-{cid}.jpg 实拍图 / .svg 墨线海报；不影响管理员后续自行设置的封面）
UPDATE product SET cover = CASE category_id
    WHEN 1 THEN '/img/cat-1.jpg'
    WHEN 2 THEN '/img/cat-2.jpg'
    WHEN 3 THEN '/img/cat-3.svg'
    WHEN 4 THEN '/img/cat-4.svg'
    WHEN 5 THEN '/img/cat-5.svg'
    ELSE '/img/cat-6.svg' END
WHERE cover IS NULL OR cover LIKE '%trae-api%' OR cover LIKE '%text_to_image%';

-- ---------------- 历史订单（仅首次初始化写入） ----------------
INSERT INTO orders (id, order_no, user_id, total_amount, status, receiver_name, receiver_phone, receiver_addr, paid_at, shipped_at, completed_at)
  SELECT 1, 'ORD20260101001', 2, 9498.00, 'COMPLETED', '爱丽丝', '13800138001', '北京市朝阳区某街道1号', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM DUAL WHERE @seed_user_data = 0 AND NOT EXISTS (SELECT 1 FROM orders WHERE order_no = 'ORD20260101001');

INSERT INTO orders (id, order_no, user_id, total_amount, status, receiver_name, receiver_phone, receiver_addr, paid_at, shipped_at)
  SELECT 2, 'ORD20260101002', 3, 1499.00, 'SHIPPED', '鲍勃', '13800138002', '上海市浦东新区某路2号', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM DUAL WHERE @seed_user_data = 0 AND NOT EXISTS (SELECT 1 FROM orders WHERE order_no = 'ORD20260101002');

INSERT INTO order_item (order_id, product_id, product_name, price, quantity, subtotal)
  SELECT 1, 1, 'iPhone 15 Pro 256GB', 8999.00, 1, 8999.00
  FROM DUAL WHERE @seed_user_data = 0 AND NOT EXISTS (SELECT 1 FROM order_item WHERE order_id = 1 AND product_id = 1);

INSERT INTO order_item (order_id, product_id, product_name, price, quantity, subtotal)
  SELECT 2, 10, '优衣库摇粒绒外套', 299.00, 1, 299.00
  FROM DUAL WHERE @seed_user_data = 0 AND NOT EXISTS (SELECT 1 FROM order_item WHERE order_id = 2 AND product_id = 10);

INSERT INTO order_item (order_id, product_id, product_name, price, quantity, subtotal)
  SELECT 2, 11, '阿迪达斯 Ultraboost', 1299.00, 1, 1299.00
  FROM DUAL WHERE @seed_user_data = 0 AND NOT EXISTS (SELECT 1 FROM order_item WHERE order_id = 2 AND product_id = 11);

-- ---------------- 示例评价（仅首次初始化写入） ----------------
INSERT INTO review (user_id, product_id, rating, content)
  SELECT 2, 1, 5, '手感非常棒，钛金属边框质感一流，拍照提升明显，值得入手。'
  FROM DUAL WHERE @seed_user_data = 0 AND NOT EXISTS (SELECT 1 FROM review WHERE user_id = 2 AND product_id = 1);

INSERT INTO review (user_id, product_id, rating, content)
  SELECT 3, 10, 4, '轻便保暖，性价比很高，唯独颜色选择少了一点。'
  FROM DUAL WHERE @seed_user_data = 0 AND NOT EXISTS (SELECT 1 FROM review WHERE user_id = 3 AND product_id = 10);

INSERT INTO review (user_id, product_id, rating, content)
  SELECT 3, 11, 5, '穿着非常舒适，缓震效果明显，跑步很合适。'
  FROM DUAL WHERE @seed_user_data = 0 AND NOT EXISTS (SELECT 1 FROM review WHERE user_id = 3 AND product_id = 11);

-- ---------------- 示例收藏（仅首次初始化写入） ----------------
INSERT INTO favorite (user_id, product_id)
  SELECT 2, 4 FROM DUAL WHERE @seed_user_data = 0 AND NOT EXISTS (SELECT 1 FROM favorite WHERE user_id = 2 AND product_id = 4);

-- ---------------- 示例地址簿（仅首次初始化写入） ----------------
INSERT INTO address (id, user_id, receiver_name, receiver_phone, region, detail, is_default)
  SELECT 1, 2, '爱丽丝', '13800138001', '北京市 朝阳区 望京街道', '融科橄榄城 3 号楼 2 单元 501 室', 1
  FROM DUAL WHERE @seed_user_data = 0 AND NOT EXISTS (SELECT 1 FROM address WHERE id = 1);
INSERT INTO address (id, user_id, receiver_name, receiver_phone, region, detail, is_default)
  SELECT 2, 2, '爱丽丝（公司）', '13800138001', '北京市 海淀区 中关村', '领航科技大厦 B 座 12 层', 0
  FROM DUAL WHERE @seed_user_data = 0 AND NOT EXISTS (SELECT 1 FROM address WHERE id = 2);
INSERT INTO address (id, user_id, receiver_name, receiver_phone, region, detail, is_default)
  SELECT 3, 3, '鲍勃', '13800138002', '上海市 浦东新区 张江', '博云路 2 号浦软大厦 606 室', 1
  FROM DUAL WHERE @seed_user_data = 0 AND NOT EXISTS (SELECT 1 FROM address WHERE id = 3);
-- 商品真实图片（联网检索并统一处理为 640x640 白底 jpg，存放于 public/img/products/）
UPDATE product SET cover = '/img/products/1.jpg' WHERE id = 1;
UPDATE product SET cover = '/img/products/2.jpg' WHERE id = 2;
UPDATE product SET cover = '/img/products/3.jpg' WHERE id = 3;
UPDATE product SET cover = '/img/products/4.jpg' WHERE id = 4;
UPDATE product SET cover = '/img/products/5.jpg' WHERE id = 5;
UPDATE product SET cover = '/img/products/6.jpg' WHERE id = 6;
UPDATE product SET cover = '/img/products/7.jpg' WHERE id = 7;
UPDATE product SET cover = '/img/products/8.jpg' WHERE id = 8;
UPDATE product SET cover = '/img/products/9.jpg' WHERE id = 9;
UPDATE product SET cover = '/img/products/10.jpg' WHERE id = 10;
UPDATE product SET cover = '/img/products/11.jpg' WHERE id = 11;
UPDATE product SET cover = '/img/products/12.jpg' WHERE id = 12;
UPDATE product SET cover = '/img/products/13.jpg' WHERE id = 13;
UPDATE product SET cover = '/img/products/14.jpg' WHERE id = 14;
UPDATE product SET cover = '/img/products/15.jpg' WHERE id = 15;
UPDATE product SET cover = '/img/products/16.jpg' WHERE id = 16;
UPDATE product SET cover = '/img/products/17.jpg' WHERE id = 17;
UPDATE product SET cover = '/img/products/18.jpg' WHERE id = 18;
UPDATE product SET cover = '/img/products/19.jpg' WHERE id = 19;
UPDATE product SET cover = '/img/products/20.jpg' WHERE id = 20;
UPDATE product SET cover = '/img/products/21.jpg' WHERE id = 21;
UPDATE product SET cover = '/img/products/22.jpg' WHERE id = 22;
UPDATE product SET cover = '/img/products/23.jpg' WHERE id = 23;
UPDATE product SET cover = '/img/products/24.jpg' WHERE id = 24;
UPDATE product SET cover = '/img/products/25.jpg' WHERE id = 25;
UPDATE product SET cover = '/img/products/26.jpg' WHERE id = 26;
UPDATE product SET cover = '/img/products/27.jpg' WHERE id = 27;
UPDATE product SET cover = '/img/products/28.jpg' WHERE id = 28;
UPDATE product SET cover = '/img/products/29.jpg' WHERE id = 29;
UPDATE product SET cover = '/img/products/30.jpg' WHERE id = 30;
UPDATE product SET cover = '/img/products/31.jpg' WHERE id = 31;
UPDATE product SET cover = '/img/products/32.jpg' WHERE id = 32;
UPDATE product SET cover = '/img/products/33.jpg' WHERE id = 33;
UPDATE product SET cover = '/img/products/34.jpg' WHERE id = 34;
UPDATE product SET cover = '/img/products/35.jpg' WHERE id = 35;
UPDATE product SET cover = '/img/products/36.jpg' WHERE id = 36;
UPDATE product SET cover = '/img/products/37.jpg' WHERE id = 37;
UPDATE product SET cover = '/img/products/38.jpg' WHERE id = 38;
UPDATE product SET cover = '/img/products/39.jpg' WHERE id = 39;
UPDATE product SET cover = '/img/products/40.jpg' WHERE id = 40;
UPDATE product SET cover = '/img/products/41.jpg' WHERE id = 41;
UPDATE product SET cover = '/img/products/42.jpg' WHERE id = 42;
UPDATE product SET cover = '/img/products/43.jpg' WHERE id = 43;
UPDATE product SET cover = '/img/products/44.jpg' WHERE id = 44;
UPDATE product SET cover = '/img/products/45.jpg' WHERE id = 45;
UPDATE product SET cover = '/img/products/46.jpg' WHERE id = 46;
UPDATE product SET cover = '/img/products/47.jpg' WHERE id = 47;
UPDATE product SET cover = '/img/products/48.jpg' WHERE id = 48;
UPDATE product SET cover = '/img/products/49.jpg' WHERE id = 49;
UPDATE product SET cover = '/img/products/50.jpg' WHERE id = 50;
UPDATE product SET cover = '/img/products/51.jpg' WHERE id = 51;
UPDATE product SET cover = '/img/products/52.jpg' WHERE id = 52;
UPDATE product SET cover = '/img/products/53.jpg' WHERE id = 53;
UPDATE product SET cover = '/img/products/54.jpg' WHERE id = 54;
UPDATE product SET cover = '/img/products/55.jpg' WHERE id = 55;
UPDATE product SET cover = '/img/products/56.jpg' WHERE id = 56;
UPDATE product SET cover = '/img/products/57.jpg' WHERE id = 57;
UPDATE product SET cover = '/img/products/58.jpg' WHERE id = 58;
UPDATE product SET cover = '/img/products/59.jpg' WHERE id = 59;
UPDATE product SET cover = '/img/products/60.jpg' WHERE id = 60;
UPDATE product SET cover = '/img/products/61.jpg' WHERE id = 61;
UPDATE product SET cover = '/img/products/62.jpg' WHERE id = 62;
UPDATE product SET cover = '/img/products/63.jpg' WHERE id = 63;
UPDATE product SET cover = '/img/products/64.jpg' WHERE id = 64;
UPDATE product SET cover = '/img/products/65.jpg' WHERE id = 65;
UPDATE product SET cover = '/img/products/66.jpg' WHERE id = 66;
UPDATE product SET cover = '/img/products/67.jpg' WHERE id = 67;
UPDATE product SET cover = '/img/products/68.jpg' WHERE id = 68;
UPDATE product SET cover = '/img/products/69.jpg' WHERE id = 69;
UPDATE product SET cover = '/img/products/70.jpg' WHERE id = 70;
UPDATE product SET cover = '/img/products/71.jpg' WHERE id = 71;
UPDATE product SET cover = '/img/products/72.jpg' WHERE id = 72;
UPDATE product SET cover = '/img/products/73.jpg' WHERE id = 73;
UPDATE product SET cover = '/img/products/74.jpg' WHERE id = 74;
UPDATE product SET cover = '/img/products/75.jpg' WHERE id = 75;
UPDATE product SET cover = '/img/products/76.jpg' WHERE id = 76;
UPDATE product SET cover = '/img/products/77.jpg' WHERE id = 77;
UPDATE product SET cover = '/img/products/78.jpg' WHERE id = 78;
UPDATE product SET cover = '/img/products/79.jpg' WHERE id = 79;
UPDATE product SET cover = '/img/products/80.jpg' WHERE id = 80;
UPDATE product SET cover = '/img/products/81.jpg' WHERE id = 81;
UPDATE product SET cover = '/img/products/82.jpg' WHERE id = 82;
UPDATE product SET cover = '/img/products/83.jpg' WHERE id = 83;
UPDATE product SET cover = '/img/products/84.jpg' WHERE id = 84;
UPDATE product SET cover = '/img/products/85.jpg' WHERE id = 85;
UPDATE product SET cover = '/img/products/86.jpg' WHERE id = 86;
UPDATE product SET cover = '/img/products/87.jpg' WHERE id = 87;
UPDATE product SET cover = '/img/products/88.jpg' WHERE id = 88;
UPDATE product SET cover = '/img/products/89.jpg' WHERE id = 89;
UPDATE product SET cover = '/img/products/90.jpg' WHERE id = 90;
UPDATE product SET cover = '/img/products/91.jpg' WHERE id = 91;
UPDATE product SET cover = '/img/products/92.jpg' WHERE id = 92;
UPDATE product SET cover = '/img/products/93.jpg' WHERE id = 93;
UPDATE product SET cover = '/img/products/94.jpg' WHERE id = 94;
UPDATE product SET cover = '/img/products/95.jpg' WHERE id = 95;
UPDATE product SET cover = '/img/products/96.jpg' WHERE id = 96;
UPDATE product SET cover = '/img/products/97.jpg' WHERE id = 97;
UPDATE product SET cover = '/img/products/98.jpg' WHERE id = 98;
UPDATE product SET cover = '/img/products/99.jpg' WHERE id = 99;
UPDATE product SET cover = '/img/products/100.jpg' WHERE id = 100;

-- ---------------- 优惠券（本轮新增） ----------------
INSERT INTO coupon (id, name, description, threshold, discount, total, claimed, start_at, end_at, status)
  SELECT 1, '新人无门槛券', '注册即领，全场通用立减 20 元', 0.00, 20.00, 999, 0, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY), 1
  WHERE NOT EXISTS (SELECT 1 FROM coupon WHERE id = 1);
INSERT INTO coupon (id, name, description, threshold, discount, total, claimed, start_at, end_at, status)
  SELECT 2, '满 300 减 50', '图书音像 / 美妆个护满减券', 300.00, 50.00, 500, 0, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY), 1
  WHERE NOT EXISTS (SELECT 1 FROM coupon WHERE id = 2);
INSERT INTO coupon (id, name, description, threshold, discount, total, claimed, start_at, end_at, status)
  SELECT 3, '满 1000 减 150', '数码 3C / 家电专属大额券', 1000.00, 150.00, 200, 0, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY), 1
  WHERE NOT EXISTS (SELECT 1 FROM coupon WHERE id = 3);
INSERT INTO coupon (id, name, description, threshold, discount, total, claimed, start_at, end_at, status)
  SELECT 4, '满 5000 减 600', '高端家电 / 旗舰真香券', 5000.00, 600.00, 50, 0, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 15 DAY), 1
  WHERE NOT EXISTS (SELECT 1 FROM coupon WHERE id = 4);

-- 店铺券：shop_id 非空，仅能在该店铺的订单上抵扣（商家可在后台自行发行本店券）
INSERT INTO coupon (id, shop_id, name, description, threshold, discount, total, claimed, start_at, end_at, status)
  SELECT 5, 2, '极客数码 · 满 500 减 60', '仅限极客数码专营店的商品使用', 500.00, 60.00, 200, 0, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY), 1
  WHERE NOT EXISTS (SELECT 1 FROM coupon WHERE id = 5);
INSERT INTO coupon (id, shop_id, name, description, threshold, discount, total, claimed, start_at, end_at, status)
  SELECT 6, 3, '生活优选 · 满 100 减 15', '仅限生活优选馆的商品使用', 100.00, 15.00, 300, 0, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY), 1
  WHERE NOT EXISTS (SELECT 1 FROM coupon WHERE id = 6);

-- 示例手机号：只在为空时补齐，不覆盖用户自己填写的号码（用户管理页要看到手机号）
UPDATE user_table SET phone = CASE username
    WHEN 'admin'     THEN '13800000001'
    WHEN 'alice'     THEN '13800138001'
    WHEN 'bob'       THEN '13800138002'
    WHEN 'charlie'   THEN '13800138003'
    WHEN 'david'     THEN '13800138004'
    WHEN 'merchant1' THEN '13900000006'
    WHEN 'merchant2' THEN '13900000007'
  END
WHERE (phone IS NULL OR phone = '')
  AND username IN ('admin','alice','bob','charlie','david','merchant1','merchant2');

-- 用户业务示例数据已初始化标记：后续重启不再补写，保证「重置数据」结果持久
INSERT IGNORE INTO seed_meta (id) VALUES (1);

-- ============================================================
-- 多商家：商家账号 + 店铺 + 商品归属
-- admin（平台管理员）同时是 1 号店店主，另外两个 MERCHANT 账号各管一家店
-- ============================================================
INSERT INTO user_table (id, username, password, nickname, email, role)
  SELECT 6, 'merchant1', '$2b$10$DYdfWxCy43FxL1kxaaJnje.dm5o.GzfWarxKz9A4y32E.x7eH52rq', '极客数码', 'merchant1@demo.com', 'MERCHANT'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_table WHERE username = 'merchant1');
INSERT INTO user_table (id, username, password, nickname, email, role)
  SELECT 7, 'merchant2', '$2b$10$DYdfWxCy43FxL1kxaaJnje.dm5o.GzfWarxKz9A4y32E.x7eH52rq', '生活优选', 'merchant2@demo.com', 'MERCHANT'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM user_table WHERE username = 'merchant2');

INSERT INTO shop (id, owner_id, name, intro, status)
  SELECT 1, 1, '星选官方旗舰店', '平台自营：手机数码与电脑办公，官方正品', 1
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM shop WHERE id = 1);
INSERT INTO shop (id, owner_id, name, intro, status)
  SELECT 2, 6, '极客数码专营店', '家用电器与图书音像，精选数码生活', 1
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM shop WHERE id = 2);
INSERT INTO shop (id, owner_id, name, intro, status)
  SELECT 3, 7, '生活优选馆', '服饰鞋包与美妆个护，日常好物', 1
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM shop WHERE id = 3);

-- 首次初始化时按分类把商品分配到三家店；后续重启不覆盖（保留商家自行调整的结果）
SET @seed_shop_data := (SELECT COUNT(*) FROM seed_meta WHERE id = 3);
UPDATE product
SET shop_id = CASE
    WHEN category_id IN (1, 2) THEN 1
    WHEN category_id IN (3, 6) THEN 2
    ELSE 3
  END
WHERE @seed_shop_data = 0;
INSERT IGNORE INTO seed_meta (id) VALUES (3);

-- ---------------- 搜索优化：手机类商品名补「手机」品类词（一次性迁移） ----------------
-- 原因：种子数据里手机型号名只有「一加 12」这类写法，搜「手机」命中不到商品名，
--       只能退化成「手机数码」整类 18 件（里面混着音箱 / 充电器 / 移动电源）。
--       补上品类词后，「手机」走名称命中这一级，精确返回 6 件手机。
SET @seed_phone_name := (SELECT COUNT(*) FROM seed_meta WHERE id = 4);
UPDATE product SET name = 'iPhone 15 Pro 256GB 5G 手机'
  WHERE @seed_phone_name = 0 AND id = 1 AND name = 'iPhone 15 Pro 256GB';
UPDATE product SET name = '小米 14 Ultra 5G 手机'
  WHERE @seed_phone_name = 0 AND id = 2 AND name = '小米 14 Ultra';
UPDATE product SET name = '华为 Mate60 Pro 手机'
  WHERE @seed_phone_name = 0 AND id = 3 AND name = '华为 Mate60 Pro';
UPDATE product SET name = 'OPPO Find X7 5G 手机'
  WHERE @seed_phone_name = 0 AND id = 25 AND name = 'OPPO Find X7';
UPDATE product SET name = 'vivo X100 Pro 5G 手机'
  WHERE @seed_phone_name = 0 AND id = 26 AND name = 'vivo X100 Pro';
UPDATE product SET name = '一加 12 5G 手机'
  WHERE @seed_phone_name = 0 AND id = 27 AND name = '一加 12';
INSERT IGNORE INTO seed_meta (id) VALUES (4);

-- ---------------- 搜索别名（keywords）：离线补列 + 100 件商品回填（一次性迁移） ----------------
-- 原因：商品名里往往没有品类词（「Nike Air Force 1」搜不到「鞋子」、「iPad Pro」搜不到「平板」），
--       光靠名称 / 分类 / 描述三级匹配仍会出现「商品存在但搜不到」。用别名字段兜住常见叫法。
-- 老库在线补列（幂等）：MySQL 的 ADD COLUMN 不支持 IF NOT EXISTS，这里查 information_schema 后动态跑
SET @has_kw_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'keywords');
SET @ddl_kw := IF(@has_kw_col = 0,
  'ALTER TABLE product ADD COLUMN keywords VARCHAR(500) NULL COMMENT ''搜索别名（逗号分隔）'' AFTER description',
  'SELECT 1');
PREPARE stmt_kw FROM @ddl_kw;
EXECUTE stmt_kw;
DEALLOCATE PREPARE stmt_kw;

SET @seed_kw := (SELECT COUNT(*) FROM seed_meta WHERE id = 5);
UPDATE product SET keywords = '手机,苹果,iPhone,5G'            WHERE @seed_kw = 0 AND id = 1;
UPDATE product SET keywords = '手机,小米,5G'                   WHERE @seed_kw = 0 AND id = 2;
UPDATE product SET keywords = '手机,华为,5G'                   WHERE @seed_kw = 0 AND id = 3;
UPDATE product SET keywords = '笔记本,电脑,苹果笔记本'          WHERE @seed_kw = 0 AND id = 4;
UPDATE product SET keywords = '笔记本,电脑,商务本'              WHERE @seed_kw = 0 AND id = 5;
UPDATE product SET keywords = '笔记本,电脑,戴尔'                WHERE @seed_kw = 0 AND id = 6;
UPDATE product SET keywords = '吸尘器,清洁电器,戴森'            WHERE @seed_kw = 0 AND id = 7;
UPDATE product SET keywords = '空调,家电'                      WHERE @seed_kw = 0 AND id = 8;
UPDATE product SET keywords = '电视,智能电视'                   WHERE @seed_kw = 0 AND id = 9;
UPDATE product SET keywords = '外套,上衣,衣服,摇粒绒'           WHERE @seed_kw = 0 AND id = 10;
UPDATE product SET keywords = '鞋子,鞋,运动鞋,跑鞋'             WHERE @seed_kw = 0 AND id = 11;
UPDATE product SET keywords = '包,手提包,女包'                  WHERE @seed_kw = 0 AND id = 12;
UPDATE product SET keywords = '精华,精华液,护肤'                WHERE @seed_kw = 0 AND id = 13;
UPDATE product SET keywords = '爽肤水,护肤,水乳'                WHERE @seed_kw = 0 AND id = 14;
UPDATE product SET keywords = '图书,书,小说,科幻'               WHERE @seed_kw = 0 AND id = 15;
UPDATE product SET keywords = '平板,平板电脑,苹果平板'          WHERE @seed_kw = 0 AND id = 16;
UPDATE product SET keywords = '耳机,蓝牙耳机,无线耳机'          WHERE @seed_kw = 0 AND id = 17;
UPDATE product SET keywords = '手表,智能手表'                   WHERE @seed_kw = 0 AND id = 18;
UPDATE product SET keywords = '耳机,头戴式耳机,降噪耳机'        WHERE @seed_kw = 0 AND id = 19;
UPDATE product SET keywords = '相机,微单,数码相机'              WHERE @seed_kw = 0 AND id = 20;
UPDATE product SET keywords = '无人机,航拍'                     WHERE @seed_kw = 0 AND id = 21;
UPDATE product SET keywords = '游戏机,掌机,switch'              WHERE @seed_kw = 0 AND id = 22;
UPDATE product SET keywords = '电子书,阅读器,墨水屏'            WHERE @seed_kw = 0 AND id = 23;
UPDATE product SET keywords = '手环,智能手环'                   WHERE @seed_kw = 0 AND id = 24;
UPDATE product SET keywords = '手机,OPPO,5G'                   WHERE @seed_kw = 0 AND id = 25;
UPDATE product SET keywords = '手机,vivo,5G'                   WHERE @seed_kw = 0 AND id = 26;
UPDATE product SET keywords = '手机,一加,5G'                   WHERE @seed_kw = 0 AND id = 27;
UPDATE product SET keywords = '充电器,快充,充电头'              WHERE @seed_kw = 0 AND id = 28;
UPDATE product SET keywords = '移动硬盘,固态硬盘,硬盘'          WHERE @seed_kw = 0 AND id = 29;
UPDATE product SET keywords = '平板,平板电脑,苹果平板'          WHERE @seed_kw = 0 AND id = 30;
UPDATE product SET keywords = '鼠标,无线鼠标,办公'              WHERE @seed_kw = 0 AND id = 31;
UPDATE product SET keywords = '键盘,无线键盘,办公'              WHERE @seed_kw = 0 AND id = 32;
UPDATE product SET keywords = '显示器,屏幕,4K显示器'            WHERE @seed_kw = 0 AND id = 33;
UPDATE product SET keywords = '显示器,屏幕,电竞显示器'          WHERE @seed_kw = 0 AND id = 34;
UPDATE product SET keywords = '键盘,平板键盘,配件'              WHERE @seed_kw = 0 AND id = 35;
UPDATE product SET keywords = '移动硬盘,硬盘,存储'              WHERE @seed_kw = 0 AND id = 36;
UPDATE product SET keywords = '摄像头,直播,vlog'                WHERE @seed_kw = 0 AND id = 37;
UPDATE product SET keywords = '扩展坞,转接头,配件'              WHERE @seed_kw = 0 AND id = 38;
UPDATE product SET keywords = '打印机,激光打印机,办公'          WHERE @seed_kw = 0 AND id = 39;
UPDATE product SET keywords = '白板,办公'                      WHERE @seed_kw = 0 AND id = 40;
UPDATE product SET keywords = '中性笔,文具,笔'                  WHERE @seed_kw = 0 AND id = 41;
UPDATE product SET keywords = '键盘,机械键盘,电竞键盘'          WHERE @seed_kw = 0 AND id = 42;
UPDATE product SET keywords = '椅子,办公椅,人体工学椅'          WHERE @seed_kw = 0 AND id = 43;
UPDATE product SET keywords = '吸尘器,无线吸尘器,戴森'          WHERE @seed_kw = 0 AND id = 44;
UPDATE product SET keywords = '吹风机,电吹风,戴森'              WHERE @seed_kw = 0 AND id = 45;
UPDATE product SET keywords = '扫地机器人,扫地机,清洁电器'      WHERE @seed_kw = 0 AND id = 46;
UPDATE product SET keywords = '扫地机器人,扫地机,擦地机'        WHERE @seed_kw = 0 AND id = 47;
UPDATE product SET keywords = '空调,挂机,家电'                 WHERE @seed_kw = 0 AND id = 48;
UPDATE product SET keywords = '洗衣机,滚筒洗衣机,家电'          WHERE @seed_kw = 0 AND id = 49;
UPDATE product SET keywords = '冰箱,对开门冰箱,家电'            WHERE @seed_kw = 0 AND id = 50;
UPDATE product SET keywords = '空气净化器,净化器,除甲醛'        WHERE @seed_kw = 0 AND id = 51;
UPDATE product SET keywords = '豆浆机,厨房电器'                 WHERE @seed_kw = 0 AND id = 52;
UPDATE product SET keywords = '电饭煲,电饭锅,厨房电器'          WHERE @seed_kw = 0 AND id = 53;
UPDATE product SET keywords = '电火锅,料理锅,厨房电器'          WHERE @seed_kw = 0 AND id = 54;
UPDATE product SET keywords = '电视,智能电视'                   WHERE @seed_kw = 0 AND id = 55;
UPDATE product SET keywords = '牙刷,电动牙刷,个护'              WHERE @seed_kw = 0 AND id = 56;
UPDATE product SET keywords = '吹风机,电吹风,护发'              WHERE @seed_kw = 0 AND id = 57;
UPDATE product SET keywords = '外套,上衣,衣服,摇粒绒'           WHERE @seed_kw = 0 AND id = 58;
UPDATE product SET keywords = '鞋子,鞋,运动鞋,跑鞋'             WHERE @seed_kw = 0 AND id = 59;
UPDATE product SET keywords = '鞋子,鞋,运动鞋,板鞋,球鞋'        WHERE @seed_kw = 0 AND id = 60;
UPDATE product SET keywords = '卫衣,上衣,衣服'                  WHERE @seed_kw = 0 AND id = 61;
UPDATE product SET keywords = '裤子,长裤,运动裤'                WHERE @seed_kw = 0 AND id = 62;
UPDATE product SET keywords = '鞋子,鞋,运动鞋,休闲鞋'           WHERE @seed_kw = 0 AND id = 63;
UPDATE product SET keywords = '羽绒服,外套,衣服,冬装'           WHERE @seed_kw = 0 AND id = 64;
UPDATE product SET keywords = '冲锋衣,外套,户外'                WHERE @seed_kw = 0 AND id = 65;
UPDATE product SET keywords = '包,托特包,女包'                  WHERE @seed_kw = 0 AND id = 66;
UPDATE product SET keywords = '背包,双肩包,包'                  WHERE @seed_kw = 0 AND id = 67;
UPDATE product SET keywords = '牛仔裤,裤子,长裤'                WHERE @seed_kw = 0 AND id = 68;
UPDATE product SET keywords = '手表,电子表,腕表'                WHERE @seed_kw = 0 AND id = 69;
UPDATE product SET keywords = '羽绒服,外套,衣服,冬装'           WHERE @seed_kw = 0 AND id = 70;
UPDATE product SET keywords = '衬衫,上衣,衣服,男装'             WHERE @seed_kw = 0 AND id = 71;
UPDATE product SET keywords = '爽肤水,护肤,水乳'                WHERE @seed_kw = 0 AND id = 72;
UPDATE product SET keywords = '精华,精华液,护肤'                WHERE @seed_kw = 0 AND id = 73;
UPDATE product SET keywords = '精华,护肤'                      WHERE @seed_kw = 0 AND id = 74;
UPDATE product SET keywords = '眼霜,护肤'                      WHERE @seed_kw = 0 AND id = 75;
UPDATE product SET keywords = '精华,护肤'                      WHERE @seed_kw = 0 AND id = 76;
UPDATE product SET keywords = '面霜,修复霜,护肤'                WHERE @seed_kw = 0 AND id = 77;
UPDATE product SET keywords = '防晒,防晒霜,护肤'                WHERE @seed_kw = 0 AND id = 78;
UPDATE product SET keywords = '口红,唇膏,彩妆'                  WHERE @seed_kw = 0 AND id = 79;
UPDATE product SET keywords = '香水,香氛,彩妆'                  WHERE @seed_kw = 0 AND id = 80;
UPDATE product SET keywords = '腮红,彩妆'                      WHERE @seed_kw = 0 AND id = 81;
UPDATE product SET keywords = '爽肤水,护肤'                     WHERE @seed_kw = 0 AND id = 82;
UPDATE product SET keywords = '染发剂,染发,个护'                WHERE @seed_kw = 0 AND id = 83;
UPDATE product SET keywords = '身体乳,身体护理,护肤'            WHERE @seed_kw = 0 AND id = 84;
UPDATE product SET keywords = '喷雾,香体,个护'                  WHERE @seed_kw = 0 AND id = 85;
UPDATE product SET keywords = '图书,书,小说,科幻'               WHERE @seed_kw = 0 AND id = 86;
UPDATE product SET keywords = '图书,书,小说'                    WHERE @seed_kw = 0 AND id = 87;
UPDATE product SET keywords = '图书,书,小说'                    WHERE @seed_kw = 0 AND id = 88;
UPDATE product SET keywords = '图书,书,小说,名著'               WHERE @seed_kw = 0 AND id = 89;
UPDATE product SET keywords = '图书,书,历史'                    WHERE @seed_kw = 0 AND id = 90;
UPDATE product SET keywords = '图书,书,计算机,编程'             WHERE @seed_kw = 0 AND id = 91;
UPDATE product SET keywords = '图书,书,算法,计算机'             WHERE @seed_kw = 0 AND id = 92;
UPDATE product SET keywords = '图书,书,编程,前端'               WHERE @seed_kw = 0 AND id = 93;
UPDATE product SET keywords = '图书,书,历史,小说'               WHERE @seed_kw = 0 AND id = 94;
UPDATE product SET keywords = '图书,书,小说,童话'               WHERE @seed_kw = 0 AND id = 95;
UPDATE product SET keywords = '黑胶,唱片,音乐'                  WHERE @seed_kw = 0 AND id = 96;
UPDATE product SET keywords = '黑胶,唱片,音乐'                  WHERE @seed_kw = 0 AND id = 97;
UPDATE product SET keywords = '音箱,音响,蓝牙音箱'              WHERE @seed_kw = 0 AND id = 98;
UPDATE product SET keywords = '图书,书,经济,教材'               WHERE @seed_kw = 0 AND id = 99;
UPDATE product SET keywords = '图书,书,考研,英语,教材'          WHERE @seed_kw = 0 AND id = 100;
INSERT IGNORE INTO seed_meta (id) VALUES (5);

-- ============================================================
-- 历史订单补店铺归属（幂等）
-- schema-mysql.sql 里的回填语句跑在种子数据之前，对全新库无效，
-- 这里在订单与明细都插入完成后按订单内商品回填，保证多商家下单时历史订单也归属到店铺
-- ============================================================
UPDATE `orders` o
JOIN (
  SELECT oi.order_id, MIN(p.shop_id) AS shop_id
  FROM order_item oi
  JOIN product p ON p.id = oi.product_id
  WHERE p.shop_id IS NOT NULL
  GROUP BY oi.order_id
) t ON t.order_id = o.id
SET o.shop_id = t.shop_id
WHERE o.shop_id IS NULL;
