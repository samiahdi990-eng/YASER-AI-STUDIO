import subprocess
import speedtest

print("\n===== تست اینترنت وای‌فای =====\n")

try:
    st = speedtest.Speedtest()
    st.get_best_server()

    print("در حال تست سرعت دانلود...")
    download = st.download() / 1_000_000

    print("در حال تست سرعت آپلود...")
    upload = st.upload() / 1_000_000

    print("\n===== نتیجه =====")
    print(f"سرعت دانلود: {download:.2f} Mbps")
    print(f"سرعت آپلود: {upload:.2f} Mbps")
    print(f"پینگ: {st.results.ping:.2f} ms")

except Exception as e:
    print("خطا در اتصال:", e)

print("\n===== بررسی اتصال اینترنت =====")

try:
    subprocess.run(
        ["ping", "-c", "4", "1.1.1.1"],
        timeout=15
    )
except Exception as e:
    print("خطا:", e)
