### 把数据库的表画出来

时光荏苒，好久不见。

随着 AI 的发展，感觉写文章好像没什么意义了。尤其是技术博客，我现在也是什么都问AI，不像以前用谷歌或者 Bing 去搜索什么技术博客，解决方案。

其实从入行开始，我就有一个想法或者发现吧。其实我们一直在忽略最重要的东西。

我们的业务，最后入库的，我们的代码，是围绕数据库编写的。

也许一开始可能会有数据库的设计图，记录着表与表之间的关系，随着业务的发展，表会越来越多，而且随着人员的流动，资料的丢失，很多表不知道是怎么关联的，到最后只有去一点一点扒代码，才能理清表的关联关系。

如果是一直维护这个系统的老手，可能还稍微好一点，可是如果是一个新手，面对一个几千张表的系统，干什么都像是在大海捞针，每天都是无尽的煎熬与痛苦。 而且开发一个复杂的业务，有时候经常会漏了这个或者那个表的字段维护。

其实这些都是不对的，不应该是这样的。

前几年我开发了一个 idea 的插件：DataTools，前前后后大半年吧，纯古法编程，这个插件算是我的集大成之作吧，可以完全取代 Idea 的 Database 插件。


![image-20260911163005890](/Users/lww/Library/Application Support/typora-user-images/image-20260911163005890.png)

![image-20260911163043239](/Users/lww/Library/Application Support/typora-user-images/image-20260911163043239.png)

![image-20260911163230566](/Users/lww/Library/Application Support/typora-user-images/image-20260911163230566.png)

![image-20260911163314825](/Users/lww/Library/Application Support/typora-user-images/image-20260911163314825.png)

![image-20260911163508864](/Users/lww/Library/Application Support/typora-user-images/image-20260911163508864.png)

![image-20260911163906771](/Users/lww/Library/Application Support/typora-user-images/image-20260911163906771.png)

1. 自己管理数据库连接
2. 根据注释搜索表
3. 根据表查询相关文件并定位
4. 代码生成
5. Excel 导出
6. 查询列
7. 表关系维护
8. 导出 PDF 和图片

