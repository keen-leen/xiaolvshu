import os
import random
import json
from datetime import datetime, timedelta
import sys

# Constants and Helper Classes

# 与后端 BCryptPasswordEncoder(12) 保持一致。该哈希对应开发种子密码 123456。
DEFAULT_PASSWORD_BCRYPT = '$2a$12$81U/nCucOHrJRPeGpZXFRONN07x8wYndkqsZ7Hm5M6Xx3PbFr1kA6'

class NotificationHelper:
    # Notification Types
    TYPES = {
        'LIKE_POST': 1,      # 点赞笔记
        'LIKE_COMMENT': 2,   # 点赞评论
        'COLLECT_POST': 3,   # 收藏笔记
        'COMMENT_POST': 4,   # 评论笔记
        'REPLY_COMMENT': 5,  # 回复评论
        'FOLLOW': 6,         # 关注
        'MENTION_COMMENT': 7, # 评论中@提及
        'MENTION': 8         # 笔记中@提及
    }

    # Notification Titles
    TITLES = {
        TYPES['LIKE_POST']: [
            '赞了你的笔记',
            '给你的笔记点了赞',
            '觉得你的笔记很赞',
            '给你点了个赞',
            '为你的笔记点赞',
            '喜欢你的笔记'
        ],
        TYPES['LIKE_COMMENT']: [
            '赞了你的评论',
            '给你的评论点了赞',
            '觉得你的评论很赞',
            '为你的评论点赞',
            '喜欢你的评论'
        ],
        TYPES['COLLECT_POST']: [
            '收藏了你的笔记',
            '把你的笔记加入收藏',
            '觉得你的内容值得收藏',
            '收藏了你的内容',
            '将你的笔记收藏了',
            '把你的作品收藏了'
        ],
        TYPES['COMMENT_POST']: [
            '评论了你的笔记',
            '在你的笔记下留言了',
            '对你的笔记发表了评论',
            '在你的内容下评论了',
            '给你的笔记留言了'
        ],
        TYPES['REPLY_COMMENT']: [
            '回复了你的评论',
            '回复了你',
            '对你的评论进行了回复',
            '回应了你的评论',
            '给你回复了'
        ],
        TYPES['FOLLOW']: [
            '关注了你',
            '成为了你的粉丝',
            '开始关注你了',
            '关注了你的账号'
        ],
        TYPES['MENTION_COMMENT']: [
            '在评论中@了你',
            '在评论中提到了你',
            '在评论中艾特了你',
            '评论中@了你',
            '提及了你'
        ],
        TYPES['MENTION']: [
            '在笔记中@了你',
            '在笔记中提到了你',
            '在笔记中艾特了你',
            '笔记中@了你',
            '提及了你'
        ]
    }

    @classmethod
    def get_random_title(cls, type_id):
        titles = cls.TITLES.get(type_id)
        if not titles:
            return '有新的通知'
        return random.choice(titles)

    @classmethod
    def create_notification_data(cls, user_id, sender_id, type_id, target_id=None, comment_id=None, is_read=False):
        return {
            'user_id': user_id,
            'sender_id': sender_id,
            'type': type_id,
            'title': cls.get_random_title(type_id),
            'target_id': target_id,
            'comment_id': comment_id,
            'is_read': 1 if is_read else 0
        }

    @classmethod
    def create_like_post_notification(cls, post_author_id, liker_id, post_id):
        return cls.create_notification_data(post_author_id, liker_id, cls.TYPES['LIKE_POST'], target_id=post_id)

    @classmethod
    def create_like_comment_notification(cls, comment_author_id, liker_id, post_id, comment_id):
        return cls.create_notification_data(comment_author_id, liker_id, cls.TYPES['LIKE_COMMENT'], target_id=post_id, comment_id=comment_id)
    
    @classmethod
    def create_collect_post_notification(cls, post_author_id, collector_id, post_id):
        return cls.create_notification_data(post_author_id, collector_id, cls.TYPES['COLLECT_POST'], target_id=post_id)

    @classmethod
    def create_comment_post_notification(cls, post_author_id, commenter_id, post_id, comment_id):
        return cls.create_notification_data(post_author_id, commenter_id, cls.TYPES['COMMENT_POST'], target_id=post_id, comment_id=comment_id)

    @classmethod
    def create_reply_comment_notification(cls, parent_comment_author_id, replier_id, post_id, reply_comment_id):
        return cls.create_notification_data(parent_comment_author_id, replier_id, cls.TYPES['REPLY_COMMENT'], target_id=post_id, comment_id=reply_comment_id)

    @classmethod
    def create_follow_notification(cls, followed_user_id, follower_id):
        return cls.create_notification_data(followed_user_id, follower_id, cls.TYPES['FOLLOW'])

    @classmethod
    def create_mention_notification(cls, mentioned_user_id, mentioner_id, post_id, comment_id):
        return cls.create_notification_data(mentioned_user_id, mentioner_id, cls.TYPES['MENTION'], target_id=post_id, comment_id=comment_id)


class SqlGenerator:
    def __init__(self):
        self.base_dir = os.path.dirname(os.path.abspath(__file__))
        self.output_file = os.path.join(self.base_dir, 'data.sql')
        self.avatar_links = self.load_links_from_file('imgLinks/avatar_link.txt')
        self.image_links = self.load_links_from_file('imgLinks/post_img_link.txt')

        self.categories = [
            '国内游', '出境游', '自驾游', '徒步登山',
            '海岛度假', '美食探店', '民宿酒店', '摄影打卡', '穷游攻略', '亲子游'
        ]

        self.category_data = {
            '国内游': {
                'name': '国内游',
                'tags': ['国内游', '自由行', '跟团游', '周边游', '古镇', '名山大川', '历史古迹', '网红景点'],
                'titles': [
                    '云南大理丽江7日深度游攻略，小众秘境全收录', '成都重庆双城记，吃货的天堂之旅', '西藏自驾318国道，一生必走一次的朝圣之路',
                    '江南水乡古镇游，乌镇西塘周庄全攻略', '张家界凤凰古城5日游，仙境般的湘西风光', '新疆独库公路自驾，绝美风景在路上',
                    '厦门鼓浪屿3日文艺之旅，小清新打卡指南', '桂林阳朔山水甲天下，漓江竹筏漂流体验', '北京故宫长城深度游，感受千年皇城魅力',
                    '青海湖茶卡盐湖环线游，天空之镜太美了'
                ],
                'contents': [
                    '这次的旅行让我对祖国的大好河山有了更深的认识，每一处风景都美得让人窒息。从繁华的都市到宁静的古镇，从巍峨的雪山到清澈的湖泊，中国的美景真的太多太多了。',
                    '精心整理了这份攻略，包含了行程安排、住宿推荐、美食打卡和避坑指南。希望能帮助到准备出行的小伙伴们，一起感受旅途的美好！',
                    '旅行的意义不仅在于看风景，更在于路上遇到的人和事。这次旅途中认识了很多有趣的朋友，听到了很多动人的故事，这些都是旅行最珍贵的收获。',
                    '整理了一份超详细的攻略，从交通、住宿到每天的行程安排都有详细介绍，还有很多省钱小技巧分享给大家！'
                ]
            },
            '出境游': {
                'name': '出境游',
                'tags': ['出境游', '日本', '泰国', '欧洲', '东南亚', '签证', '免税购物', '异国风情'],
                'titles': [
                    '日本关西7日游攻略，京都大阪奈良一网打尽', '泰国清迈曼谷自由行，小众寺庙和网红咖啡厅', '新加坡马来西亚双国游，东南亚风情体验',
                    '韩国首尔济州岛5日游，追星购物美食全攻略', '越南芽庄岘港游记，高性价比的海滨度假', '巴厘岛蜜月之旅，最浪漫的海岛婚纱照',
                    '欧洲15日深度游，法意瑞三国精华景点', '澳大利亚自驾游记，大洋路的绝美风光', '迪拜阿布扎比土豪之旅，奢华体验分享',
                    '冰岛环岛自驾，追极光看冰川的梦幻之旅'
                ],
                'contents': [
                    '终于实现了出国旅行的愿望！异国的文化、美食、风景都让人印象深刻。虽然语言不通有时候会遇到一些小困难，但这也是旅行有趣的地方。',
                    '整理了这次出境游的全部经验，包括签证办理、机票酒店预订、当地交通、必去景点和购物退税等，希望对准备出国的朋友有帮助。',
                    '第一次出国旅行，心情既兴奋又紧张。但当飞机降落在异国土地上的那一刻，所有的担心都烟消云散了。世界那么大，真的要出去看看！',
                    '这次旅行让我对不同的文化有了更深的理解和尊重，旅行真的是最好的学习方式。分享一些我的见闻和感悟。'
                ]
            },
            '自驾游': {
                'name': '自驾游',
                'tags': ['自驾游', '房车', '公路旅行', '露营', '越野', '租车', '自驾路线', '风景公路'],
                'titles': [
                    '川藏线自驾全攻略，最美景观大道深度体验', '青甘大环线自驾游，西北风光一览无遗', '海南环岛自驾游，椰风海韵的热带天堂',
                    '内蒙古草原自驾游，策马奔腾的自由感觉', '云南滇藏线自驾，从热带到雪山的奇妙旅程', '新疆独库公路自驾，一条路穿越四季',
                    '318国道骑行记录，两轮上的诗和远方', '房车自驾游初体验，移动的家太香了', '福建沿海自驾游，最美海岸线公路',
                    '贵州山地自驾游，喀斯特地貌的震撼之美'
                ],
                'contents': [
                    '自驾游的魅力在于自由，想停就停想走就走。沿途的风景、偶遇的惊喜、意外的发现，都是跟团游无法体验到的。分享我的自驾心得和路线规划。',
                    '整理了这次自驾游的详细攻略，包括路线规划、加油站位置、住宿推荐、必备物品清单等，希望对准备自驾出行的朋友有帮助。',
                    '开着车行驶在辽阔的公路上，两旁的风景不断变化，从草原到雪山，从沙漠到湖泊，这种感觉真的太棒了！自驾游，永远在路上。',
                    '自驾游需要做好充分的准备，车辆检查、路线规划、应急物品等都很重要。分享一些我的自驾经验和注意事项。'
                ]
            },
            '徒步登山': {
                'name': '徒步登山',
                'tags': ['徒步', '登山', '户外', '露营', '雪山', '徒步路线', '装备', '高原'],
                'titles': [
                    '四姑娘山大峰攀登记，人生第一座雪山', '徒步雨崩村攻略，梅里雪山脚下的秘境', '武功山云海日出，最美高山草甸徒步',
                    '黄山两日徒步攻略，日出云海松迎客', '华山长空栈道体验，中国最险峻的山峰', '稻城亚丁徒步记录，香格里拉的最后净土',
                    '泰山夜爬攻略，凌晨登顶看日出', '玉龙雪山一日游，雪山之下的蓝月谷', '尼泊尔ABC徒步，喜马拉雅山下的震撼',
                    '哈巴雪山攀登记录，5396米的挑战与感动'
                ],
                'contents': [
                    '登山的过程虽然辛苦，但当你站在山顶俯瞰群山的那一刻，所有的疲惫都值得了。山就在那里，等待着每一个勇敢的攀登者。',
                    '整理了这次徒步的详细攻略，包括路线介绍、装备清单、体能准备、高反预防等，希望能帮助到想要挑战的朋友。',
                    '徒步让我学会了坚持，学会了在困难面前不放弃。每一步的前进都是对自己的超越，这种成就感是无法用言语形容的。',
                    '户外徒步需要做好充分的准备，安全永远是第一位的。分享一些我的徒步经验和装备推荐。'
                ]
            },
            '海岛度假': {
                'name': '海岛度假',
                'tags': ['海岛', '沙滩', '潜水', '浮潜', '日落', '度假村', '蜜月', '海景房'],
                'titles': [
                    '马尔代夫蜜月之旅，一岛一酒店的极致体验', '三亚亚龙湾度假攻略，国内最美海滩推荐', '普吉岛自由行，泰国海岛的热带风情',
                    '涠洲岛3日游攻略，小众海岛的质朴之美', '巴厘岛乌布度假，海岛与文化的完美结合', '长滩岛日落帆船体验，最美的黄昏时光',
                    '仙本那潜水攻略，探索海底的奇妙世界', '塞班岛蓝洞浮潜，清澈见底的太平洋', '斐济蜜月游记，世界尽头的浪漫天堂',
                    '苏梅岛5日慢生活，泰国最悠闲的海岛'
                ],
                'contents': [
                    '海岛度假是最让人放松的旅行方式，躺在沙滩上吹着海风，听着海浪的声音，所有的烦恼都随风而去。这就是我心中的完美假期。',
                    '整理了这次海岛游的攻略，包括岛屿选择、酒店推荐、水上项目、美食打卡等，希望对准备去海岛度假的朋友有帮助。',
                    '潜水让我发现了一个全新的世界，海底的珊瑚、热带鱼、海龟都美得不真实。如果你还没试过潜水，一定要去体验一次！',
                    '选择海岛度假就是选择慢生活，不需要赶景点、不需要早起，只需要尽情享受阳光、沙滩和海浪。'
                ]
            },
            '美食探店': {
                'name': '美食探店',
                'tags': ['美食', '探店', '小吃', '网红餐厅', '地方特色', '夜市', '老字号', '米其林'],
                'titles': [
                    '成都美食地图，最地道的川菜和小吃推荐', '西安回民街美食攻略，碳水爱好者的天堂', '广州早茶探店，从虾饺到叉烧包全测评',
                    '长沙夜宵指南，臭豆腐小龙虾一网打尽', '厦门中山路小吃攻略，沙茶面海蛎煎必吃', '台北夜市美食攻略，一晚上吃遍台湾味',
                    '日本东京美食地图，从拉面到寿司全推荐', '曼谷街头美食探秘，泰式风味大满足', '重庆火锅测评，最辣最香的十家店推荐',
                    '潮汕美食之旅，牛肉火锅和各种粿品'
                ],
                'contents': [
                    '旅行的意义之一就是品尝当地美食，每一道菜都承载着一个地方的文化和历史。这次美食之旅让我的味蕾得到了极大的满足！',
                    '整理了这座城市的美食攻略，从老字号到网红店，从早餐到夜宵，每一家都是我亲自探访的真实推荐。',
                    '都说要了解一个地方，就要从它的美食开始。这次旅行我用味觉记录了这座城市，每一口都是满满的幸福感。',
                    '美食探店是我旅行中最喜欢的环节，发现隐藏在街巷里的宝藏小店，品尝最地道的当地风味，这就是旅行的乐趣所在。'
                ]
            },
            '民宿酒店': {
                'name': '民宿酒店',
                'tags': ['民宿', '酒店', '住宿', '海景房', '山景房', '设计酒店', '度假村', '青旅'],
                'titles': [
                    '大理洱海边民宿推荐，躺在床上就能看日出', '莫干山精品民宿测评，隐居山林的完美体验', '丽江古城民宿推荐，纳西风格的温馨小院',
                    '三亚海景房酒店对比，教你选到性价比最高的', '日本温泉旅馆体验，泡汤赏枫的惬意时光', '阳朔漓江边民宿推荐，推开窗就是山水画',
                    '厦门鼓浪屿特色民宿，文艺小清新风格推荐', '西双版纳雨林酒店，住进热带雨林的感觉', '泸沽湖湖景民宿推荐，摩梭风情的独特体验',
                    '香格里拉藏式酒店，感受浓郁的藏族文化'
                ],
                'contents': [
                    '住宿是旅行体验的重要组成部分，一家好的民宿或酒店能让旅行更加完美。分享一些我住过的宝藏住宿，希望对大家的选择有帮助。',
                    '这次住的民宿真的太惊艳了！从设计风格到服务细节，每一处都让人感到温馨和舒适。强烈推荐给大家！',
                    '精心整理了这个目的地的住宿攻略，从预算青旅到高端度假村，各种价位和风格的推荐都有，总有一款适合你。',
                    '选择住宿不仅要看价格，位置、设施、服务、风格都很重要。分享一些我的选房经验和踩坑教训。'
                ]
            },
            '摄影打卡': {
                'name': '摄影打卡',
                'tags': ['摄影', '打卡', '网红景点', '拍照', '日落', '星空', '航拍', '旅拍'],
                'titles': [
                    '重庆网红打卡点合集，洪崖洞李子坝全攻略', '新疆独库公路最美拍照点，每一帧都是壁纸', '厦门文艺打卡地图，小清新照片这样拍',
                    '西藏星空拍摄攻略，银河和雪山同框的震撼', '日本京都和服旅拍，古风照片拍摄技巧', '巴厘岛网红打卡点，ins风照片全指南',
                    '青海茶卡盐湖拍照攻略，天空之镜出片秘籍', '摩洛哥撒哈拉沙漠旅拍，一生必去的网红地', '冰岛极光拍摄攻略，追光者的终极指南',
                    '成都宽窄巷子打卡攻略，最出片的角度推荐'
                ],
                'contents': [
                    '整理了这次旅行的所有拍照点和拍摄技巧，从机位选择到后期调色，教你拍出刷爆朋友圈的旅行照片！',
                    '旅行摄影是记录美好的最佳方式，分享一些我的拍照心得和后期技巧，希望能帮助大家拍出更美的旅行照片。',
                    '这些网红打卡点真的太出片了！不需要专业设备，手机也能拍出大片效果。分享具体的拍摄时间、角度和构图技巧。',
                    '旅行最重要的就是记录美好瞬间，一张好照片胜过千言万语。分享我的旅拍经验和修图APP推荐。'
                ]
            },
            '穷游攻略': {
                'name': '穷游攻略',
                'tags': ['穷游', '省钱', '背包客', '预算旅行', '性价比', '特价机票', '青旅', '免费景点'],
                'titles': [
                    '东南亚穷游攻略，人均3000玩转泰国越南', '学生党国内穷游指南，1000元玩一周', '青旅住宿全攻略，省钱又能交朋友',
                    '特价机票购买技巧，教你买到白菜价', '欧洲穷游攻略，如何用最少的钱玩转欧洲', '日本省钱攻略，不花冤枉钱的旅行秘籍',
                    '免费景点合集，这些地方不要钱也超美', '背包客旅行装备清单，轻装上阵省钱省力', '沙发客体验分享，免费住宿的奇妙旅程',
                    '搭便车旅行记录，一路上的温暖与感动'
                ],
                'contents': [
                    '谁说旅行一定要花很多钱？只要做好攻略、合理规划，用很少的预算也能有精彩的旅行体验。分享我的穷游经验和省钱技巧。',
                    '整理了这次穷游的全部花费明细，从交通、住宿到餐饮、门票，每一笔都记录清楚，证明低预算也能玩得很嗨！',
                    '旅行的意义不在于花多少钱，而在于路上的风景和遇到的人。背包客的旅行方式让我更加深入地体验当地生活。',
                    '省钱不是抠门，而是把钱花在刀刃上。分享一些实用的省钱技巧，让你的旅行预算花得更值。'
                ]
            },
            '亲子游': {
                'name': '亲子游',
                'tags': ['亲子游', '带娃旅行', '游乐园', '动物园', '海洋馆', '研学', '家庭游', '暑假'],
                'titles': [
                    '上海迪士尼带娃攻略，排队最少玩最多的秘籍', '三亚亲子游攻略，海边溜娃的完美行程', '成都大熊猫基地亲子游，和国宝近距离接触',
                    '香港海洋公园全攻略，孩子最爱的项目推荐', '日本带娃旅行攻略，亲子友好的目的地', '珠海长隆海洋王国，最详细的游玩攻略',
                    '北京亲子游景点推荐，寓教于乐的首都之旅', '新加坡亲子游攻略，环球影城动物园全覆盖', '云南亲子研学游，大自然就是最好的课堂',
                    '西安历史文化亲子游，让孩子爱上历史'
                ],
                'contents': [
                    '带娃旅行虽然累但很值得，看到孩子开心的笑脸，所有的辛苦都变成了幸福。分享这次亲子游的经验和心得。',
                    '整理了这份超详细的亲子游攻略，包括适合孩子的景点推荐、餐厅选择、住宿建议和行程安排，带娃出行必看！',
                    '旅行是最好的教育，让孩子在旅途中开阔眼界、增长见识。分享一些亲子游的目的地推荐和注意事项。',
                    '带孩子旅行需要做更多的准备工作，从行李清单到应急方案，分享我的亲子游准备经验。'
                ]
            }
        }

        self.usernames = [
            '云游四海', '背包客小妹', '说走就走', '环球旅行家', '徒步天涯', '星辰大海', '追风少年', '山野漫步',
            '海岛控', '古镇迷', '自驾狂人', '露营达人', '摄影旅人', '美食猎人', '民宿探索者', '穷游侠',
            '旅行日记', '行走的风景', '远方的诗', '候鸟迁徙', '浪迹天涯', '梦想旅行家', '地图收藏家', '足迹遍天下',
            '山川湖海', '风景猎人', '旅途故事', '世界那么大', '说走就走的旅行', '在路上', '诗和远方', '漫游世界',
            '背包去流浪', '追逐日落', '沙滩椰树', '雪山探险家', '森林漫步', '城市漫游者', '古迹探秘', '人文行者',
            '旅行青蛙', '独行侠', '环游世界梦', '自由行达人', '攻略收割机', '打卡狂魔', '旅行体验官', '游记作者', '世界观察家', '旅途中的风景'
        ]
        self.locations = [
            '北京', '上海', '广州', '深圳', '杭州', '成都', '重庆', '西安', '南京', '武汉',
            '天津', '苏州', '长沙', '郑州', '青岛', '大连', '厦门', '福州', '昆明', '贵阳',
            '南宁', '海口', '三亚', '拉萨', '乌鲁木齐', '银川', '西宁', '兰州', '呼和浩特', '哈尔滨',
            '长春', '沈阳', '石家庄', '太原', '济南', '合肥', '南昌', '温州', '宁波', '无锡',
            '常州', '徐州', '扬州', '镇江', '泰州', '盐城', '淮安', '连云港', '宿迁', '嘉兴'
        ]
        self.sql_buffer = []

    def load_links_from_file(self, filename):
        try:
            file_path = os.path.join(self.base_dir, filename)
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            return [line.strip() for line in content.split('\n') if line.strip()]
        except Exception as e:
            print(f"读取文件 {filename} 失败: {e}")
            return []

    def generate_random_image_url(self):
        if not self.image_links:
            return 'http://dummyimage.com/400x300'
        return random.choice(self.image_links)

    def generate_random_avatar_url(self):
        if not self.avatar_links:
            return 'http://dummyimage.com/100x100'
        return random.choice(self.avatar_links)

    def append_sql(self, sql):
        self.sql_buffer.append(sql + ";\n")

    def escape(self, val):
        if val is None:
            return 'NULL'
        if isinstance(val, (int, float)):
            return str(val)
        if isinstance(val, bool):
            return '1' if val else '0'
        # Simple escaping
        val_str = str(val)
        val_str = val_str.replace('\\', '\\\\').replace("'", "\\'").replace('\n', '\\n')
        return f"'{val_str}'"

    def generate(self):
        print('开始生成SQL脚本...')
        self.append_sql("-- 小旅书 模拟数据 SQL 脚本")
        self.append_sql("SET NAMES utf8mb4")

        # 清空表
        tables = [
            'user_sessions', 'notifications', 'comments', 'collections',
            'likes', 'post_tags', 'follows', 'post_images', 'posts',
            'tags', 'users', 'admin', 'categories', 'audit', 'post_videos'
        ]
        for table in tables:
            self.append_sql(f"TRUNCATE TABLE {table}")

        # 1. 生成管理员
        print('生成管理员SQL...')
        admins = [
            {'id': 1, 'username': 'admin', 'password': '123456'},
            {'id': 2, 'username': 'admin2', 'password': '123456'},
            {'id': 3, 'username': 'admin3', 'password': '123456'}
        ]
        for admin in admins:
            self.append_sql(f"INSERT INTO admin (id, username, password) VALUES ({admin['id']}, {self.escape(admin['username'])}, {self.escape(DEFAULT_PASSWORD_BCRYPT)})")

        # 2. 生成用户
        print('生成用户SQL...')
        users = self.generate_users(50)
        for i, user in enumerate(users):
            user['id'] = i + 1
            last_login_str = user['last_login_at'].strftime('%Y-%m-%d %H:%M:%S')
            self.append_sql(f"INSERT INTO users (id, user_id, password, nickname, avatar, bio, location, follow_count, fans_count, like_count, is_active, last_login_at, gender, zodiac_sign, mbti, education, major, interests) VALUES ({user['id']}, {self.escape(user['user_id'])}, {self.escape(DEFAULT_PASSWORD_BCRYPT)}, {self.escape(user['nickname'])}, {self.escape(user['avatar'])}, {self.escape(user['bio'])}, {self.escape(user['location'])}, {user['follow_count']}, {user['fans_count']}, {user['like_count']}, {user['is_active']}, {self.escape(last_login_str)}, {self.escape(user['gender'])}, {self.escape(user['zodiac_sign'])}, {self.escape(user['mbti'])}, {self.escape(user['education'])}, {self.escape(user['major'])}, {self.escape(user['interests'])})")

        # 3. 生成分类
        print('生成分类SQL...')
        categories = self.generate_categories_data()
        for i, cat in enumerate(categories):
            cat['id'] = i + 1
            self.append_sql(f"INSERT INTO categories (id, name, category_title) VALUES ({cat['id']}, {self.escape(cat['name'])}, {self.escape(cat['category_title'])})")

        # 4. 生成标签
        print('生成标签SQL...')
        tags = self.generate_tags()
        for i, tag in enumerate(tags):
            tag['id'] = i + 1
            self.append_sql(f"INSERT INTO tags (id, name, use_count) VALUES ({tag['id']}, {self.escape(tag['name'])}, {tag['use_count']})")

        # 5. 生成笔记
        print('生成笔记SQL...')
        posts = self.generate_posts(len(users), 200)
        for i, post in enumerate(posts):
            post['id'] = i + 1
            self.append_sql(f"INSERT INTO posts (id, user_id, title, content, category_id, type, is_draft, view_count, like_count, collect_count, comment_count) VALUES ({post['id']}, {post['user_id']}, {self.escape(post['title'])}, {self.escape(post['content'])}, {post['category_id']}, 1, {post['is_draft']}, {post['view_count']}, {post['like_count']}, {post['collect_count']}, {post['comment_count']})")

        # 统计用户发帖数和分类帖子数
        user_post_stats = [0] * (len(users) + 1)
        category_post_stats = [0] * (len(categories) + 1)
        for post in posts:
            if post['is_draft'] == 0:  # 只统计非草稿的帖子
                user_post_stats[post['user_id']] += 1
                category_post_stats[post['category_id']] += 1
        
        # 更新用户表的 post_count
        for user in users:
            self.append_sql(f"UPDATE users SET post_count = {user_post_stats[user['id']]} WHERE id = {user['id']}")
        
        # 更新分类表的 post_count
        for cat in categories:
            self.append_sql(f"UPDATE categories SET post_count = {category_post_stats[cat['id']]} WHERE id = {cat['id']}")

        # 6. 生成笔记图片
        print('生成笔记图片SQL...')
        post_images = self.generate_post_images(len(posts))
        for img in post_images:
            self.append_sql(f"INSERT INTO post_images (post_id, image_url) VALUES ({img['post_id']}, {self.escape(img['image_url'])})")

        # 7. 生成关注关系
        print('生成关注关系SQL...')
        follows = self.generate_follows(len(users))
        user_follow_stats = [0] * (len(users) + 1)
        user_fans_stats = [0] * (len(users) + 1)
        for f in follows:
            self.append_sql(f"INSERT INTO follows (follower_id, following_id) VALUES ({f['follower_id']}, {f['following_id']})")
            user_follow_stats[f['follower_id']] += 1
            user_fans_stats[f['following_id']] += 1
        
        # 更新用户表统计的SQL
        for user in users:
            self.append_sql(f"UPDATE users SET follow_count = {user_follow_stats[user['id']]}, fans_count = {user_fans_stats[user['id']]} WHERE id = {user['id']}")

        # 8. 生成点赞、收藏、评论
        print('生成互动数据SQL...')
        comments = self.generate_comments(users, len(posts))
        for c in comments:
            parent_id = c['parent_id'] if c['parent_id'] else 'NULL'
            self.append_sql(f"INSERT INTO comments (id, post_id, user_id, parent_id, content, like_count) VALUES ({c['id']}, {c['post_id']}, {c['user_id']}, {parent_id}, {self.escape(c['content'])}, {c['like_count']})")

        # 更新 post comment count
        post_comment_stats = [0] * (len(posts) + 1)
        for c in comments:
            post_comment_stats[c['post_id']] += 1
        for i in range(1, len(posts) + 1):
            self.append_sql(f"UPDATE posts SET comment_count = {post_comment_stats[i]} WHERE id = {i}")

        likes = self.generate_likes(len(users), len(posts), len(comments))
        post_like_stats = [0] * (len(posts) + 1)
        user_like_stats = [0] * (len(users) + 1)

        for like in likes:
            self.append_sql(f"INSERT INTO likes (user_id, target_type, target_id) VALUES ({like['user_id']}, {like['target_type']}, {like['target_id']})")
            if like['target_type'] == 1: # post
                post_like_stats[like['target_id']] += 1
                # Array is 0-indexed, id is 1-based
                post = posts[like['target_id'] - 1]
                user_like_stats[post['user_id']] += 1
        
        # Update post like count
        for i in range(1, len(posts) + 1):
            self.append_sql(f"UPDATE posts SET like_count = {post_like_stats[i]} WHERE id = {i}")
        # Update user like count
        for i in range(1, len(users) + 1):
            self.append_sql(f"UPDATE users SET like_count = {user_like_stats[i]} WHERE id = {i}")

        collections = self.generate_collections(len(users), len(posts))
        post_collect_stats = [0] * (len(posts) + 1)
        for col in collections:
            self.append_sql(f"INSERT INTO collections (user_id, post_id) VALUES ({col['user_id']}, {col['post_id']})")
            post_collect_stats[col['post_id']] += 1
        
        # Update post collect count
        for i in range(1, len(posts) + 1):
            self.append_sql(f"UPDATE posts SET collect_count = {post_collect_stats[i]} WHERE id = {i}")

        # 9. 笔记与标签关联
        print('生成笔记标签关联SQL...')
        tag_use_stats = [0] * (len(tags) + 1)
        for post_id in range(1, len(posts) + 1):
            tag_count = random.randint(1, 3)
            used_tags = set()
            for _ in range(tag_count):
                tag_id = 0
                while True:
                    tag_id = random.randint(1, len(tags))
                    if tag_id not in used_tags:
                        break
                used_tags.add(tag_id)
                self.append_sql(f"INSERT INTO post_tags (post_id, tag_id) VALUES ({post_id}, {tag_id})")
                tag_use_stats[tag_id] += 1
        
        for tag in tags:
            self.append_sql(f"UPDATE tags SET use_count = {tag_use_stats[tag['id']]} WHERE id = {tag['id']}")

        # 10. 通知
        print('生成通知数据SQL...')
        notifications = []
        # Like notifications
        for like in likes:
            if like['target_type'] == 1: # Post
                post = posts[like['target_id'] - 1]
                if post and like['user_id'] != post['user_id']:
                    notification_data = NotificationHelper.create_like_post_notification(post['user_id'], like['user_id'], like['target_id'])
                    notification_data['is_read'] = 1 if random.random() > 0.4 else 0
                    notifications.append(notification_data)
            elif like['target_type'] == 2: # Comment
                comment = next((c for c in comments if c['id'] == like['target_id']), None)
                if comment and like['user_id'] != comment['user_id']:
                    notification_data = NotificationHelper.create_like_comment_notification(comment['user_id'], like['user_id'], comment['post_id'], comment['id'])
                    notification_data['is_read'] = 1 if random.random() > 0.4 else 0
                    notifications.append(notification_data)
        
        # Comment notifications
        for comment in comments:
            import re
            if comment['content'] and 'mention-link' in comment['content']:
                mention_matches = re.finditer(r'data-user-id="([^"]+)"', comment['content'])
                for match in mention_matches:
                    mentioned_user_display_id = match.group(1)
                    mentioned_user = next((u for u in users if u['user_id'] == mentioned_user_display_id), None)
                    if mentioned_user and mentioned_user['id'] != comment['user_id']:
                        notification_data = NotificationHelper.create_mention_notification(mentioned_user['id'], comment['user_id'], comment['post_id'], comment['id'])
                        notification_data['is_read'] = 1 if random.random() > 0.4 else 0
                        notifications.append(notification_data)
            
            if comment['parent_id']:
                parent_comment = next((c for c in comments if c['id'] == comment['parent_id']), None)
                if parent_comment and comment['user_id'] != parent_comment['user_id']:
                    notification_data = NotificationHelper.create_reply_comment_notification(parent_comment['user_id'], comment['user_id'], comment['post_id'], comment['id'])
                    notification_data['is_read'] = 1 if random.random() > 0.4 else 0
                    notifications.append(notification_data)
            else:
                post = posts[comment['post_id'] - 1]
                if post and comment['user_id'] != post['user_id']:
                    notification_data = NotificationHelper.create_comment_post_notification(post['user_id'], comment['user_id'], comment['post_id'], comment['id'])
                    notification_data['is_read'] = 1 if random.random() > 0.4 else 0
                    notifications.append(notification_data)

        # Follow notifications
        for follow in follows:
            notification_data = NotificationHelper.create_follow_notification(follow['following_id'], follow['follower_id'])
            notification_data['is_read'] = 1 if random.random() > 0.4 else 0
            notifications.append(notification_data)

        # Collection notifications
        for collection in collections:
            post = posts[collection['post_id'] - 1]
            if post and collection['user_id'] != post['user_id']:
                notification_data = NotificationHelper.create_collect_post_notification(post['user_id'], collection['user_id'], collection['post_id'])
                notification_data['is_read'] = 1 if random.random() > 0.4 else 0
                notifications.append(notification_data)

        for n in notifications:
            has_undefined = any(v is None for k, v in n.items() if k in ['user_id', 'sender_id', 'type', 'target_id'] and k != 'target_id') # target_id can be None for some? NO, follow has None target_id
            # JS: [n.user_id, n.sender_id, n.type, n.title, n.target_id].some(p => p === undefined); 
            # In python dict.get() returns None if default.
            
            # Check mandatory fields
            if n.get('user_id') is None or n.get('sender_id') is None or n.get('type') is None or n.get('title') is None:
                 continue
            # Note regarding target_id: In JS code, it checked target_id too.
            # But Follow notification has no target_id.
            # Let's check logic: Follow notification types are 6.
            # JS code: 
            # const hasUndefined = [n.user_id, n.sender_id, n.type, n.title, n.target_id].some(p => p === undefined);
            # in JS, if target_id is omitted in object creation, it is undefined.
            # NotificationHelper.createFollowNotification DOES NOT set targetId. So targetId is undefined.
            # Wait, the JS code specifically checks if target_id is undefined and skips it!
            # "if(!hasUndefined)" -> if hasUndefined is false -> if NO field is undefined.
            # This implies Follow notifications in JS script were likely SKIPPED because target_id was undefined?
            # Let's double check.
            # In JS: createFollowNotification returns { ... targetId: undefined ... } if not passed? 
            # No, createNotificationData has `targetId = null` default. `null` is not `undefined`.
            # So `p === undefined` check passes for `null`.
            # So Follow notifications (targetId=null) ARE INCLUDED.
            # My Python `create_notification_data` sets target_id=None default.
            
            target_id = n.get('target_id')
            target_str = str(target_id) if target_id is not None else 'NULL'
            
            comment_id = n.get('comment_id')
            comment_str = str(comment_id) if comment_id is not None else 'NULL'

            self.append_sql(f"INSERT INTO notifications (user_id, sender_id, type, title, target_id, comment_id, is_read) VALUES ({n['user_id']}, {n['sender_id']}, {n['type']}, {self.escape(n['title'])}, {target_str}, {comment_str}, {n['is_read']})")

        # 11. Sessions
        print('生成会话数据SQL...')
        sessions = self.generate_user_sessions(len(users))
        for s in sessions:
            expires_str = s['expires_at'].strftime('%Y-%m-%d %H:%M:%S')
            self.append_sql(f"INSERT INTO user_sessions (user_id, token, refresh_token, expires_at, user_agent, is_active) VALUES ({s['user_id']}, {self.escape(s['token'])}, {self.escape(s['refresh_token'])}, {self.escape(expires_str)}, {self.escape(s['user_agent'])}, {s['is_active']})")

        # 写入文件
        with open(self.output_file, 'w', encoding='utf-8') as f:
            f.writelines(self.sql_buffer)
        print(f"SQL脚本已生成: {self.output_file}")


    def generate_users(self, count=50):
        users = []
        bios = [
            '热爱旅行，用脚步丈量世界 ✨', '一个爱探索的旅行者，分享路上的风景 😊', '背包客 | 世界那么大，我想去看看 🎒', '摄影旅行者 | 用镜头记录每一次旅途 📷',
            '美食旅行家 | 吃遍各地特色美食 🍜', '旅行达人 | 已打卡30+国家和地区 ✈️', '户外探险者 | 征服每一座山峰 🏔️', '自驾游爱好者 | 在路上的自由最可贵 🚗',
            '海岛控 | 收集世界上最美的沙滩 🏝️', '古镇迷 | 寻找那些被遗忘的时光 🏮', '民宿体验官 | 每一间都是独特的风景 🏠', '徒步爱好者 | 最美的风景在路上 🥾',
            '穷游背包客 | 用最少的钱看最美的风景 💰', '旅行vlogger | 记录旅途中的精彩 🎬', '环球旅行梦 | 把世界装进行李箱 🧳', '说走就走派 | 不等待，去远方 🌸',
            '文化旅行者 | 探索世界各地的人文风情 🏛️', '冒险家 | 挑战极限，超越自我 🧗', '旅行摄影师 | 每一帧都是回忆 📸', '背包去流浪 | 自由是最好的旅行方式 🎒',
            '美景收藏家 | 用眼睛记住每一处风景 👁️', '旅行笔记 | 用文字记录旅途故事 ✒️', '户外达人 | 露营徒步样样精通 ⛺', '海岛度假控 | 向往阳光沙滩的生活 ☀️',
            '自由行爱好者 | 拒绝跟团，享受自由 🗺️', '旅行体验官 | 分享最真实的旅行体验 📝', '公路旅行家 | 最美风景在路上 🛣️', '极简旅行者 | 一个背包走天下 🎒',
            '深度游玩家 | 不赶景点，慢慢体验 🐢', '旅行种草机 | 分享小众宝藏目的地 💎', '打卡达人 | 网红景点一个不落 📍', '旅行攻略控 | 做攻略是旅行的一部分 📋',
            '追日落的人 | 收集世界各地的日落 🌅', '星空猎人 | 追逐银河和极光 🌌', '咖啡旅行家 | 打卡各地特色咖啡馆 ☕', '博物馆控 | 感受历史文化的魅力 🏛️',
            '亲子旅行达人 | 带娃看世界 👨‍👩‍👧', '蜜月旅行分享 | 记录甜蜜时光 💕', '独行侠 | 一个人的旅行更自由 🚶', '旅行社畜 | 用假期治愈工作的疲惫 🏃',
            '自然爱好者 | 亲近大自然的美好 🌿', '冰雪运动迷 | 追逐世界各地的雪场 ⛷️', '温泉爱好者 | 泡遍天下温泉 ♨️', '海钓达人 | 享受海上的宁静时光 🎣',
            '潜水爱好者 | 探索神秘的海底世界 🤿', '骑行旅行者 | 用两轮丈量世界 🚴', '房车旅行家 | 带着家去旅行 🚐', '探险家 | 去没人去过的地方 🧭',
            '旅行养生派 | 在旅行中放松身心 🧘', '旅途中的风景 | 每一次出发都是期待 🌈'
        ]
        genders = ['male', 'female']
        zodiac_signs = ['白羊座', '金牛座', '双子座', '巨蟹座', '狮子座', '处女座', '天秤座', '天蝎座', '射手座', '摩羯座', '水瓶座', '双鱼座']
        mbti_types = ['INTJ', 'INTP', 'ENTJ', 'ENTP', 'INFJ', 'INFP', 'ENFJ', 'ENFP', 'ISTJ', 'ISFJ', 'ESTJ', 'ESFJ', 'ISTP', 'ISFP', 'ESTP', 'ESFP']
        educations = ['高中', '大专', '本科', '硕士', '博士']
        majors = ['旅游管理', '酒店管理', '地理学', '历史学', '摄影', '新闻传播', '外语', '市场营销', '计算机科学', '设计学', '建筑学', '经济学', '金融学', '会计学', '工商管理', '法学', '心理学', '教育学', '医学', '生物学', '化学', '物理学', '数学', '艺术设计', '音乐', '美术', '体育']
        interest_options = ['旅行', '摄影', '美食', '徒步', '登山', '潜水', '滑雪', '露营', '自驾', '骑行', '冲浪', '帆船', '攀岩', '跳伞', '滑翔伞', '热气球', '观星', '追极光', '古镇探访', '博物馆', '咖啡', '品酒', '民宿体验', '航拍', '写游记', '收集冰箱贴', '集邮', '地图收藏', '明信片', '旅行手帐', '语言学习', '文化探索', '历史古迹', '自然风光', '海岛度假', '温泉', '瑜伽']

        for i in range(count):
            user_interests = []
            interest_count = random.randint(2, 5)
            shuffled_interests = random.sample(interest_options, interest_count)
            user_interests = shuffled_interests

            user = {
                'user_id': f"user{str(i + 1).zfill(3)}",
                'password': '123456',
                'nickname': self.usernames[i % len(self.usernames)],
                'avatar': self.generate_random_avatar_url(),
                'bio': random.choice(bios),
                'location': self.locations[i % len(self.locations)],
                'follow_count': random.randint(0, 500),
                'fans_count': random.randint(0, 1000),
                'like_count': random.randint(0, 5000),
                'is_active': 1,
                'last_login_at': datetime.now() - timedelta(milliseconds=random.randint(0, 30 * 24 * 60 * 60 * 1000)),
                'gender': random.choice(genders) if random.random() > 0.3 else None,
                'zodiac_sign': random.choice(zodiac_signs) if random.random() > 0.3 else None,
                'mbti': random.choice(mbti_types) if random.random() > 0.3 else None,
                'education': random.choice(educations) if random.random() > 0.3 else None,
                'major': random.choice(majors) if random.random() > 0.3 else None,
                'interests': json.dumps(user_interests, ensure_ascii=False) if random.random() > 0.3 else None,
                'verified': 0
            }
            users.append(user)
        return users

    def generate_categories_data(self):
        category_mapping = {'国内游': 'domestic', '出境游': 'abroad', '自驾游': 'roadtrip', '徒步登山': 'hiking', '海岛度假': 'island', '美食探店': 'food', '民宿酒店': 'hotel', '摄影打卡': 'photography', '穷游攻略': 'budget', '亲子游': 'family'}
        return [{'name': name, 'category_title': category_mapping[name]} for name in self.categories]

    def generate_tags(self):
        all_tags = []
        for category in self.category_data.values():
            all_tags.extend(category['tags'])
        unique_tags = list(set(all_tags))
        return [{'name': tag, 'use_count': random.randint(10, 210)} for tag in unique_tags]

    def generate_posts(self, user_count, count=200):
        posts = []
        for _ in range(count):
            category_index = random.randint(0, len(self.categories) - 1)
            category = self.categories[category_index]
            category_info = self.category_data[category]
            
            post = {
                'user_id': random.randint(1, user_count),
                'title': random.choice(category_info['titles']),
                'content': random.choice(category_info['contents']),
                'category_id': category_index + 1,
                'is_draft': 0,
                'view_count': random.randint(0, 10000),
                'like_count': 0, # Calculated later
                'collect_count': 0, # Calculated later
                'comment_count': 0 # Calculated later
            }
            posts.append(post)
        return posts

    def generate_post_images(self, post_count, max_images_per_post=5):
        images = []
        for post_id in range(1, post_count + 1):
            image_count = random.randint(1, max_images_per_post)
            for _ in range(image_count):
                images.append({'post_id': post_id, 'image_url': self.generate_random_image_url()})
        return images

    def generate_follows(self, user_count, count=300):
        follows = []
        used = set()
        for _ in range(count):
            follower_id = 0
            following_id = 0
            while True:
                follower_id = random.randint(1, user_count)
                following_id = random.randint(1, user_count)
                if follower_id != following_id and f"{follower_id}-{following_id}" not in used:
                    break
            used.add(f"{follower_id}-{following_id}")
            follows.append({'follower_id': follower_id, 'following_id': following_id})
        return follows

    def generate_likes(self, user_count, post_count, comment_count, count=1000):
        likes = []
        used = set()
        for _ in range(count):
            user_id = 0
            target_id = 0
            target_type = 2 if random.random() > 0.8 else 1
            while True:
                user_id = random.randint(1, user_count)
                if target_type == 1:
                    target_id = random.randint(1, post_count)
                else:
                    target_id = random.randint(1, comment_count)
                
                key = f"{user_id}-{target_type}-{target_id}"
                if key not in used:
                    used.add(key)
                    break
            likes.append({'user_id': user_id, 'target_type': target_type, 'target_id': target_id})
        return likes

    def generate_collections(self, user_count, post_count, count=400):
        collections = []
        used = set()
        for _ in range(count):
            user_id = 0
            post_id = 0
            while True:
                user_id = random.randint(1, user_count)
                post_id = random.randint(1, post_count)
                key = f"{user_id}-{post_id}"
                if key not in used:
                    used.add(key)
                    break
            collections.append({'user_id': user_id, 'post_id': post_id})
        return collections

    def generate_comments(self, users, post_count, count=800):
        comments = []
        comment_texts = [
            # 表达赞同和支持
            '太实用了，收藏了！', '攻略做得真详细', '感谢分享', '种草了，准备去', '风景太美了', '写得很好', '很有帮助', '马住了',
            '同去过，真的很推荐', '行程安排得很棒', '谢谢分享攻略', '看完就想出发', '已经加入心愿清单', '拍得太好看了', '这个地方绝了',
            '说得太对了！', '深有同感', '受益匪浅', '太有用了', '必须点赞', '说到心坎里了', '完全同意',
            # 表达疑问和讨论
            '人均多少呀', '请问住的哪家酒店？', '交通方便吗？', '几月份去最好？', '能详细说说怎么订票吗？', '有没有更详细的攻略？', '一个人去安全吗？',
            '有个问题想请教一下', '这个路线真的推荐吗？', '能详细说说吗？', '有没有更好的建议？',
            '有没有遇到过这种情况？', '求更多细节', '能分享下具体行程吗？', '有类似经历',
            # 补充建议
            '我上次去也是这样的体验', '补充一点，那边还有...', '我觉得还可以去这里...', '建议多留一天时间', '另外推荐一下附近的...',
            '建议可以试试...', '我一般会这样安排', '还有一个小技巧', '注意这个细节',
            '我的做法是...', '推荐一个APP', '可以参考这个', '类似的还有...',
            # 表达感谢和鼓励
            '谢谢楼主的分享', '真的帮到我了', '正好需要这个', '解决了我的困惑', '及时雨啊',
            '楼主太厉害了', '继续加油', '期待更多分享', '关注了', '马克一下',
            # 表达情感共鸣
            '太真实了', '说出了我的心声', '感同身受', '我也是这样想的', '引起共鸣了',
            '看哭了', '太感动了', '很温暖', '正能量满满', '很治愈',
            # 日常互动
            '码住！', '先收藏再说', '已收藏，等假期', '太棒了！', '默默点赞', '学习了', '顶一个', '好文章必须支持',
            '沙发！', '前排支持', '来晚了', '围观学习', '路过留名', '值得收藏', '转发了', '分享给朋友',
            # 旅行相关
            '这就是说走就走的旅行啊', '想念旅行的时光', '疫情后第一站就去这', '已经在做攻略了',
            '风景绝美', '照片拍得太棒了', '请问用什么设备拍的？', '后期调色教程有吗？',
            # 网络用语
            '666', '牛啊', 'yyds', '绝了', '太强了', '服气', '厉害厉害',
            '学废了', '我酸了', '柠檬精上线', '这就是差距', '人比人气死人'
        ]
        mention_comment_templates = [
            ' 这个地方不错，你可以看看 ', ' 这攻略挺有用的，分享给你 ', ' 这个目的地值得一去，你肯定会喜欢 ', ' 觉得这个对你计划旅行有帮助 ',
            ' 这个挺好的，推荐给你 ', ' 看到这个就想到你了，来看看吧 ', ' 这个内容不错，你可能会喜欢 ', ' 这个挺有价值的，分享给你 ',
            ' 这个值得关注，你可以了解下 ', ' 觉得这个不错，你也看看吧 ', ' 这个内容挺好，推荐你看一下 ', ' 这个挺实用的，你可以参考下 ',
            ' 看到这个就想让你也看看 ', ' 这个内容不错，分享给你参考 ', ' 这个挺有意思，你过来看看 '
        ]
        user_count = len(users)
        comments_by_post = {}

        for i in range(count):
            post_id = random.randint(1, post_count)
            if post_id not in comments_by_post:
                comments_by_post[post_id] = []
            
            existing_comments_for_post = comments_by_post[post_id]
            parent_id = None
            if len(existing_comments_for_post) > 0 and random.random() > 0.7:
                parent_comment = random.choice(existing_comments_for_post)
                parent_id = parent_comment['id']
            
            content = ""
            if random.random() < 0.15:
                mentioned_user = random.choice(users)
                mention_text = random.choice(mention_comment_templates)
                content = f'<p><a href="/user/{mentioned_user["user_id"]}" data-user-id="{mentioned_user["user_id"]}" class="mention-link" contenteditable="false">@{mentioned_user["nickname"]}</a>&nbsp;{mention_text}</p>'
            else:
                content = random.choice(comment_texts)

            comment = {
                'id': i + 1,
                'post_id': post_id,
                'user_id': random.randint(1, user_count),
                'parent_id': parent_id,
                'content': content,
                'like_count': random.randint(0, 20)
            }
            comments_by_post[post_id].append(comment)
            comments.append(comment)
        return comments

    def generate_user_sessions(self, user_count):
        sessions = []
        user_agents = [
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1',
            'Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
            'Mozilla/5.0 (iPad; CPU OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1'
        ]
        
        for user_id in range(1, user_count + 1):
            now_ts = int(datetime.now().timestamp() * 1000)
            rand_str = ''.join(random.choices('0123456789abcdefghijklmnopqrstuvwxyz', k=9))
            
            session = {
                'user_id': user_id,
                'token': f"token_{now_ts}_{rand_str}",
                'refresh_token': f"refresh_{now_ts}_{rand_str}",
                'expires_at': datetime.now() + timedelta(days=7),
                'user_agent': random.choice(user_agents),
                'is_active': 1 if random.random() > 0.2 else 0
            }
            sessions.append(session)
        return sessions

if __name__ == '__main__':
    SqlGenerator().generate()
