package ice

typealias Test = suspend (Int) -> Int

inline suspend fun tester(r: Test) {
    r(3)
}