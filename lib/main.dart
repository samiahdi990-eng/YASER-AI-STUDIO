import 'package:flutter/material.dart';

void main() {
  runApp(const YaserAI());
}

const purple = Color(0xFF9B5CFF);
const dark = Color(0xFF090711);

class YaserAI extends StatelessWidget {
  const YaserAI({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'YASER AI STUDIO',
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: dark,
        colorScheme: ColorScheme.fromSeed(
          seedColor: purple,
          brightness: Brightness.dark,
        ),
      ),
      home: const HomePage(),
    );
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    final tools = [
      ['ساخت تصویر', Icons.image, 'تبدیل متن به تصویر'],
      ['عکس به ویدیو', Icons.movie_creation, 'جان بخشیدن به عکس'],
      ['حذف پس‌زمینه', Icons.layers_clear, 'پاک کردن پس‌زمینه'],
      ['ساخت لوگو', Icons.auto_awesome, 'طراحی لوگوی اختصاصی'],
      ['ویرایش تصویر', Icons.tune, 'ویرایش حرفه‌ای عکس'],
      ['دستیار هوشمند', Icons.smart_toy, 'گفتگو با هوش مصنوعی'],
    ];

    return Directionality(
      textDirection: TextDirection.rtl,
      child: Scaffold(
        body: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [purple, Color(0xFF4B1FA8)],
                        ),
                        borderRadius: BorderRadius.circular(15),
                      ),
                      child: const Icon(
                        Icons.auto_awesome,
                        color: Colors.white,
                        size: 28,
                      ),
                    ),
                    const SizedBox(width: 12),
                    const Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'YASER AI',
                          style: TextStyle(
                            fontSize: 21,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Text(
                          'استودیوی هوش مصنوعی',
                          style: TextStyle(color: Colors.white54),
                        ),
                      ],
                    ),
                    const Spacer(),
                    const CircleAvatar(
                      backgroundColor: Color(0xFF241638),
                      child: Icon(Icons.person, color: purple),
                    ),
                  ],
                ),
                const SizedBox(height: 28),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(22),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [
                        Color(0xFF5422A0),
                        Color(0xFF211238),
                        Color(0xFF11101D),
                      ],
                    ),
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'خلاقیت خود را آزاد کن!',
                        style: TextStyle(
                          fontSize: 23,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 10),
                      const Text(
                        'با قدرت هوش مصنوعی، ایده‌هایت را به واقعیت تبدیل کن.',
                        style: TextStyle(color: Colors.white70),
                      ),
                      const SizedBox(height: 18),
                      ElevatedButton.icon(
                        onPressed: () {},
                        icon: const Icon(Icons.auto_awesome),
                        label: const Text('شروع خلق اثر'),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 28),
                const Text(
                  'ابزارهای هوشمند',
                  style: TextStyle(
                    fontSize: 19,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 15),
                GridView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: tools.length,
                  gridDelegate:
                      const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: 1.12,
                  ),
                  itemBuilder: (context, index) {
                    final tool = tools[index];

                    return Container(
                      padding: const EdgeInsets.all(15),
                      decoration: BoxDecoration(
                        color: const Color(0xFF171322),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(
                          color: purple.withOpacity(0.22),
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(
                            tool[1] as IconData,
                            color: purple,
                            size: 31,
                          ),
                          const SizedBox(height: 12),
                          Text(
                            tool[0] as String,
                            style: const TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 15,
                            ),
                          ),
                          const SizedBox(height: 5),
                          Text(
                            tool[2] as String,
                            style: const TextStyle(
                              fontSize: 10,
                              color: Colors.white54,
                            ),
                          ),
                        ],
                      ),
                    );
                  },
                ),
                const SizedBox(height: 25),
                Container(
                  padding: const EdgeInsets.all(17),
                  decoration: BoxDecoration(
                    color: const Color(0xFF171322),
                    borderRadius: BorderRadius.circular(18),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.auto_awesome, color: purple, size: 28),
                      SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'YASER AI STUDIO',
                              style: TextStyle(fontWeight: FontWeight.bold),
                            ),
                            SizedBox(height: 4),
                            Text(
                              'دنیایی از خلاقیت در دستان تو',
                              style: TextStyle(
                                color: Colors.white54,
                                fontSize: 11,
                              ),
                            ),
                          ],
                        ),
                      ),
                      Icon(Icons.favorite, color: purple),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
