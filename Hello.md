### 把数据库的表画出来

### 古法编程

时光荏苒，好久不见。

随着 AI 的发展，感觉写文章好像没什么意义了。尤其是技术博客，我现在也是什么都问AI，不像以前用谷歌或者 Bing 去搜索什么技术博客，解决方案。

其实从入行开始，我就有一个想法或者发现吧，其实我们一直在忽略最重要的东西。

我们的业务，最后是要入库的；我们的代码，是围绕数据库编写的。

也许一开始可能会有数据库的设计图，记录着表与表之间的关系，随着业务的发展，表会越来越多，而且随着人员的流动，资料的丢失，很多表不知道是怎么关联的，到最后只有去一点一点扒代码，才能理清表的关联关系。

如果是一直维护这个系统的老手，可能还稍微好一点，可是如果是一个新手，面对一个几千张表的系统，干什么都像是在大海捞针，每天都是无尽的煎熬与痛苦。 而且开发一个复杂的业务，有时候经常会漏了这个或者那个表的字段维护。

其实不应该是这样的。

前几年我开发了一个 idea 的插件：DataTools，前前后后大半年吧，纯古法编程，这个插件算是集大成之作吧，对标 Idea 的 Database 插件。

![image-20260911163314825](/Users/lww/Library/Application Support/typora-user-images/image-20260911163314825.png)



![image-20260911163906771](/Users/lww/Library/Application Support/typora-user-images/image-20260911163906771.png)

主要就是自己管理数据库连接；根据表的名称可以查询相关文件并导航过去；生成代码；导出 Excel；最重要的就是可以维护表之间的关系，然后可以导出 pdf 和图片。

不过一直都是自己在用，没有发布到 idea 插件市场。

因为一开始自己想用一段时间，找找 bug 然后修复一下。后面觉得很多功能和 Database 重叠了。

而且数据库支持的种类太少，只支持 mysql，Oracle，pg。自己一个个适配又太麻烦，这个地方也是我最不满意的地方。

还有数据库的连接，关闭项目后，再次打开，偶尔出现不会自动连接。

然后就一直自己用，没有发布了。



### AI 改造

这几个月，AI 的发展可以说是一日万里，其实我也用 AI 开发了很多软件，桌面端，web，App 都有，后面会慢慢给大家分享。

话说回来，其实我一直觉得，这个插件还是不错的，而且很有必要的，上面的一大串原因就不再赘述了。

刚好 AI 这么厉害，为什么不重写一下这个插件呢？

之前用了半年多，现在差不多一个星期就把主要的功能开发好了，不过后续又打磨了一些细节的东西。

所以 `DataChart` 就横空出世了。

### 新建文件

首先新增了一种文件格式：`.datachart`，可以通过新增创建一个 `datachart` 文件

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260921124048691.png" alt="image-20260921124048691" style="zoom:50%;" />

创建完成后，双击打开就会看到一个画板。因为针对这个格式的文件，又自定义了编辑器。

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260921124547641.png" alt="image-20260921124547641" style="zoom:50%;" />

为什么用文件管理？因为用文件管理，就可以和代码一起来管理和共享了，就算是刚入职的新手，把代码拉下来，看着图也能知道各种业务的表结构和关联了。

### 添加表

添加表是从 `Database` 把表拖进来的，会自动生成表结构卡片（字段、类型、注释、主键 / 索引标识）。

为什么没有自己管理数据库连接？因为自己管理太麻烦了，还要找各种驱动，用 `idea` 的 `Database`，就不用去找驱动了，而且理论上只要 `idea` 支持的数据库，这个插件都支持。

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260921125107210.png" alt="image-20260921125107210" style="zoom:50%;" />

每行的图标也是遵循了 `idea` 的规则

还可以再拖一张表进来，看到辅助线了么？支持自动对齐，有轻微的磁吸效果。

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924161152886.png" alt="image-20260924161152886" style="zoom:50%;" />

### 连线

可以把表连起来

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924161322847.png" alt="image-20260924161322847" style="zoom:50%;" />

可以设置对应关系

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924161419304.png" alt="image-20260924161419304" style="zoom:50%;" />

我们多连几张（下图是导出图片的效果）

![用户_20260924_165320](/Users/lww/用户_20260924_165320.jpg)

还可以导出 `pdf`，是支持文本搜索的 `pdf` 哦！

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924172457619.png" alt="image-20260924172457619" style="zoom:50%;" />

不过插件也支持搜索，输入完之后回车就可以啦。

![image-20260924171302720](/Users/lww/Library/Application Support/typora-user-images/image-20260924171302720.png)

### 菜单功能

#### 复制、跳转

一些简单的小功能，还有跳转，可以查看表的 `DDL` 语句，查看数据，还有在 `Database Explorer` 中定位表

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924165638086.png" alt="image-20260924165638086" style="zoom:50%;" />

表头和列上面菜单是不一样的。而且列的前半部分右键是菜单，后面就是拉线出来了。

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924171404835.png" alt="image-20260924171404835" style="zoom:50%;" />

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924171512852.png" alt="image-20260924171512852" style="zoom:50%;" />

#### 同步表结构

如果修改了表的列，怎么办？就需要同步表结构了。

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924171820881.png" alt="image-20260924171820881" style="zoom:50%;" />

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924171953368.png" alt="image-20260924171953368" style="zoom:50%;" />

新增和删除的列会有一个效果，不过是一次性的，关闭再打开就没有了。

#### 查找引用

这个是调用 `idea` 的 `Find`

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924165932979.png" alt="image-20260924165932979" style="zoom:50%;" />

### AI

插件没有 `AI` 功能，不过你用文本打开 `datachart` 文件，会发现它就是个 `json`

第一个元素就是 `aiGuide`

![image-20260924170707181](/Users/lww/Library/Application Support/typora-user-images/image-20260924170707181.png)

因此你可以通过`AI` 编程插件来根据 `datachart` 文件写 `sql`

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924170317381.png" alt="image-20260924170317381" style="zoom:50%;" />

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924170544498.png" alt="image-20260924170544498" style="zoom:50%;" />

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924170615188.png" alt="image-20260924170615188" style="zoom:50%;" />

<img src="/Users/lww/Library/Application Support/typora-user-images/image-20260924170637916.png" alt="image-20260924170637916" style="zoom:50%;" />

### 放大缩小

画板是可以放大缩小的，通过 `command`+滚轮，或者 `ctrl`+鼠标滚轮 可以放大缩小画板。

还有一些其他细节的地方就不多说了，大家去体验吧。



### 地址

[GitHub](https://github.com/DavieLiu115/DataChart)

[插件地址](https://plugins.jetbrains.com/plugin/34597-datachart)
